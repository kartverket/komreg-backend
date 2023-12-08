package no.kartverket.komreg.routes

import arrow.core.raise.result
import arrow.core.raise.zipOrAccumulate
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.resourceScope
import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.db.DBAppender
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.db.DataSourceConnectionSource
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.*
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.kartverket.komreg.core.logging.FAG
import no.kartverket.komreg.repositories.Kjoring
import no.kartverket.komreg.repositories.KjoringRepo
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

@Serializable
data class ExecutionLogEventDTO(
    val executionId: Int,
    val firstEventRef: LogEventRef,
    val lastEventRef: LogEventRef?
) {
    @Serializable
    data class LogEventRef(
        val eventId: Long,
        val eventTime: Instant
    )

    inline val firstEventId get() = firstEventRef.eventId
    inline val firstEventTime get() = firstEventRef.eventTime
    inline val lastEventId get() = lastEventRef?.eventId
    inline val lasEventTime get() = lastEventRef?.eventTime
}

@Serializable
data class LogEventDTO(
    val eventId: Long,
    val levelString: String,
    val timestamp: Instant,
    val loggerName: String,
    val formattedMessage: String?
)

private const val ROW_BUFFER_SIZE = 64
private const val DEFAULT_PAGE_SIZE = 100.toShort()
private val TZ = TimeZone.of("Europe/Oslo")

fun Application.enableFagLogging(komregDbPool: DataSource) {
    val sharedFlow = MutableSharedFlow<Pair<Long, LogEventDTO>>()
    when (val loggerContext = LoggerFactory.getILoggerFactory()) {
        is LoggerContext -> {
            val dbAppender = object : DBAppender() {
                var seqNo = 0L

                override fun secondarySubAppend(event: ILoggingEvent, connection: Connection?, eventId: Long) {
                    super.secondarySubAppend(event, connection, eventId)
                    runBlocking {
                        val dto = LogEventDTO(
                            eventId,
                            event.level.levelStr,
                            Instant.fromEpochMilliseconds(event.timeStamp),
                            event.loggerName,
                            event.formattedMessage
                        )
                        sharedFlow.emit(seqNo to dto)
                        seqNo = seqNo.inc()
                    }
                }
            }.apply {
                name = "KOMREG_DB"
                context = loggerContext
                connectionSource = DataSourceConnectionSource().apply {
                    dataSource = komregDbPool
                    discoverConnectionProperties()
                }
                start()
            }

            val asyncAppender = AsyncAppender().apply {
                name = "KOMREG_DB_ASYNC"
                context = loggerContext
                addAppender(dbAppender)
                addFilter(object : Filter<ILoggingEvent>() {
                    override fun decide(event: ILoggingEvent): FilterReply {
                        return if (event.markerList.contains(FAG)) {
                            FilterReply.ACCEPT
                        } else {
                            FilterReply.DENY
                        }
                    }
                })
                start()
            }

            loggerContext.getLogger("ROOT").addAppender(asyncAppender)

            environment.monitor.subscribe(ApplicationStarted) {
                log.info(FAG, "Komreg startet")
            }

            environment.monitor.subscribe(ApplicationStopping) {
                log.info(FAG, "Komreg stoppet")
                val shutdown = result {
                    zipOrAccumulate(
                        { t, suppressed -> t.apply { addSuppressed(suppressed) } },
                        { asyncAppender.detachAndStopAllAppenders() },
                        { asyncAppender.stop() },
                        { dbAppender.stop() }) { _, _, _ ->
                        // Unit
                    }
                }
                shutdown.onFailure {
                    log.error("Feil under nedstenging av fag-logger", it)
                }
            }
        }

        else -> {
            log.error(
                """
                    **************************************************************************************
                    * LOGBACK ER *IKKE* LOGGING BACKEND, LOGGING AV FAGMELDINGER TIL DATABASE DEAKTIVERT *
                    **************************************************************************************
                """.trimIndent()
            )
        }
    }

    loggingRoutes(komregDbPool, sharedFlow)
}

private fun Application.loggingRoutes(dataSource: DataSource, eventFlow: Flow<Pair<Long, LogEventDTO>>) {

    routing {
        tempUiRoutes(dataSource)
        route("/log/api") {
            get("live") {
                call.eventsLive(eventFlow)
            }
            get("/executions/{executionId?}") {
                val executionId = call.parameters["executionId"]
                    ?.takeUnless { it.isBlank() }
                    ?.toInt()
                val page = call.request.queryParameters["page"]
                    ?.toInt()
                    ?: 0
                val pageSize = call.request.queryParameters["page_size"]
                    ?.toShort()
                    ?: DEFAULT_PAGE_SIZE

                if (executionId != null) {
                    call.respond(
                        fetchLogEventDTOs(dataSource, executionId, page, pageSize)
                            .illegalArgumentToBadRequest()
                            .toList()
                    )
                } else {
                    val fromEventId = call.request.queryParameters["from_event_id"]?.toLong() ?: 0
                    call.respond(
                        fetchExecutionLogEventDTOs(dataSource, fromEventId, page, pageSize)
                            .illegalArgumentToBadRequest()
                            .toList()
                    )
                }
            }
        }
    }
}

private fun Routing.tempUiRoutes(dataSource: DataSource) {
    val kjoringRepo = KjoringRepo(dataSource)
    route("/log") {
        get {
            call.respondRedirect("/log/executions")
        }
        route("/executions") {
            get("/{executionId}") {
                val executionId = call.parameters["executionId"]?.toIntOrNull() ?: throw NotFoundException()
                val page = call.request.queryParameters["page"]?.toInt() ?: 0
                val pageSize = call.request.queryParameters["page_size"]?.toShort() ?: DEFAULT_PAGE_SIZE
                call.respondTextWriter(contentType = ContentType.Text.Html) {
                    write(
                        """
                        <html>
                            <head><title>Kjøring $executionId</title></head>
                            <body>
                            <table style='width: 100%'>
                            <title>Kjøring $executionId</title>
                            <thead style="position: sticky; top: 0">
                                <tr>
                                    <th class="kjoring_id">Event ID</th>
                                    <th class="level">Level</th>
                                    <th class="datetime">Timestamp</th>
                                    <th>Message</th>
                                </tr>
                            </thead>
                            <tbody>
                    """.trimIndent()
                    )
                    fetchLogEventDTOs(dataSource, executionId, page, pageSize).onEach {
                        write(
                            """
                            <tr>
                                <td class="kjoring_id">${it.eventId}</td>
                                <td class="level">${it.levelString}</td>
                                <td class="datetime">
                                    ${it
                                        .timestamp
                                        .toLocalDateTime(TZ)
                                        .toJavaLocalDateTime()
                                        .format(dateTimeFormatter)}
                                </td>
                                <td>${it.formattedMessage ?: ""}</td>
                            </tr>
                        """.trimIndent()
                        )
                    }.flowOn(Dispatchers.IO).collect()
                    write(
                        """
                        </tbody>
                        </table>
                        <ul>
                            <li><a href="/log/executions/$executionId?page=${page.inc()}">Neste side</a></li>
                            """
                    )
                    if (page > 0) {
                        write("""<li><a href="/log/executions/$executionId?page=${page.dec()}">Forrige side</a></li>""")
                    }
                    write(
                        """
                            <li><a href="/log/executions/$executionId?page=0">Første side</a></li>
                            <li><a href="/log/executions">Tilbake til oversikt</a></li>
                        </ul>
                        $defaultStyle
                        </body>
                        </html>""".trimIndent()
                    )
                }
            }
            get {
                val kjoringer = kjoringRepo.getKjoringer().associateBy(Kjoring::id)
                call.respondTextWriter(contentType = ContentType.Text.Html) {
                    write(
                        """
                        <html>
                            <head><title>Loggede kjøringer</title></head>
                            <body>
                            <table style='width: 100%'>
                            <title>Kjøringer</title>
                            <thead>
                                <tr>
                                    <th class="kjoring_id">Kjøring ID</th>
                                    <th>Regulering</th>
                                    <th class="datetime">Første timestamp</th>
                                    <th class="datetime">Siste timestamp</th>
                                </tr>
                            </thead>
                            <tbody>
                    """.trimIndent()
                    )
                    fetchExecutionLogEventDTOs(dataSource, 0L, -1).toList().reversed().forEach { execution ->
                        write(
                            """
                            <tr>
                                <td class="kjoring_id"><a href="/log/executions/${execution.executionId}">${execution.executionId}</a></td>
                                <td>${kjoringer[execution.executionId]?.regulering ?: "(ukjent)"}</td>
                                <td class="datetime">
                                    ${execution
                                        .firstEventTime
                                        .toLocalDateTime(TZ)
                                        .toJavaLocalDateTime()
                                        .format(dateTimeFormatter)}
                                 </td>
                                <td class="datetime">
                                  ${execution
                                      .lasEventTime
                                      ?.toLocalDateTime(TZ)
                                      ?.toJavaLocalDateTime()
                                      ?.format(dateTimeFormatter)
                                      ?: ""}
                                </td>
                            </tr>
                        """.trimIndent()
                        )
                    }
                    write(
                        """
                        </tbody>
                        </table>
                        <ul>
                            <li><a href="/log/live">Live visning</a></li>
                        </ul>
                        $defaultStyle
                        </body>
                        </html>""".trimIndent()
                    )
                }
            }
        }
        get("/live") {
            call.respondText(
                """
                        <html>
                            <head><title>Live FAG logg</title></head>
                            <body>
                            <table style='width: 100%'>
                            <title>Logg</title>

                            <thead>
                                <tr>
                                    <th class="kjoring_id">Event ID</th>
                                    <th class="level">Level</th>
                                    <th class="datetime">Timestamp</th>
                                    <th>Message</th>
                                </tr>
                            </thead>
                            <tbody id="log"/>
                            </table>
                            <ul>
                            <li><a href="/log/executions">Tilbake til oversikt</a></li>
                            </ul>
                                <script type="text/javascript">
                                    const source = new EventSource('/log/api/live');
                                    const logTable = document.getElementById('log');
                                    const dateFormat = new Intl.DateTimeFormat("nb-NO", {
                                        year: "2-digit", 
                                        month: "2-digit", 
                                        day: "2-digit", 
                                        hour: "2-digit", 
                                        minute: "2-digit", 
                                        second: "2-digit", 
                                        fractionalSecondDigits: 3
                                    })

                                    function logEvent(logEvent) {
                                        console.log(logEvent);
                                        const event = JSON.parse(logEvent);
                                        const row = document.createElement('tr');
                                        const eventId = document.createElement('td');
                                        eventId.className = 'kjoring_id';
                                        const level = document.createElement('td');
                                        level.className = 'level';
                                        const timestamp = document.createElement('td');
                                        timestamp.className = 'datetime';
                                        const message = document.createElement('td');
                                        eventId.innerText = event.eventId;
                                        level.innerText = event.levelString;
                                        timestamp.innerText = dateFormat.format(Date.parse(event.timestamp));
                                        message.innerText = event.formattedMessage;
                                        row.appendChild(eventId);
                                        row.appendChild(level);
                                        row.appendChild(timestamp);
                                        row.appendChild(message);
                                        logTable.appendChild(row);
                                        
                                        window.scrollTo(0, document.body.scrollHeight);
                                    }

                                    source.addEventListener('message', function(e) {
                                        logEvent(e.data);
                                    }, false);

                                    source.addEventListener('open', function(e) {
                                        console.log('open: ' + e)
                                    }, false);

                                    source.addEventListener('error', function(e) {
                                        if (e.readyState === EventSource.CLOSED) {
                                            console.log('closed: ' + e);
                                        } else {
                                            console.log('error: ' + e);
                                        }
                                    }, false);
                                </script>
                                $defaultStyle
                            </body>
                        </html>
                    """.trimIndent(),
                contentType = ContentType.Text.Html
            )
        }
    }
}

private suspend fun ApplicationCall.eventsLive(
    eventFlow: Flow<Pair<Long, LogEventDTO>>,
) {
    response.cacheControl(CacheControl.NoCache(null))
    respondOutputStream(contentType = ContentType.Text.EventStream) {
        eventFlow
            .runningReduce { (prevSeq, _), seqAndEvent ->
                val (seq, _) = seqAndEvent
                if (prevSeq.inc() != seq) {
                    error("Missed events: ${prevSeq.inc()} != $seq")
                }
                seqAndEvent
            }
            .map { it.second }
            .onEach {
                write("id: ${it.eventId}\n".toByteArray())
                Json.encodeToString(it).split("\n").forEach { line ->
                    write("data: ".toByteArray())
                    write(line.toByteArray())
                    write("\r\n\r\n".toByteArray())
                }
                flush()
            }
            .catch { cause ->
                close()
                throw IllegalStateException("Error while streaming events", cause)
            }
            .flowOn(Dispatchers.IO)
            .collect()
    }
}

private suspend fun fetchExecutionLogEventDTOs(
    dataSource: DataSource,
    fromEventId: Long,
    page: Int,
    pageSize: Short = DEFAULT_PAGE_SIZE
): Flow<ExecutionLogEventDTO> = flow {
    val (
        @Suppress("NAME_SHADOWING")
        page,
        sortDirection
    ) = pageAndSortOrder(page)

    pageSize.requireGtZero("pageSize")

    val sql = """
        SELECT 
          kjoring_id, first_event_id, first_timestamp, last_event_id, last_timestamp
          FROM 
            (SELECT
                 row_number() OVER () - 1 row_number,
                 q.kjoring_id,
                 fst.event_id first_event_id,
                 fst.timestmp first_timestamp,
                 lst.event_id last_event_id,
                 lst.timestmp last_timestamp
             FROM 
                 (SELECT lep.mapped_value  kjoring_id,
                         min(lep.event_id) fst_event_id,
                         max(lep.event_id) lst_event_id
                  FROM logging_event_property lep
                  WHERE 
                    mapped_key = 'kjoringId'
                    AND event_id >= ?
                  GROUP BY lep.mapped_value) q 
                 JOIN logging_event fst ON q.fst_event_id = fst.event_id
                 LEFT JOIN logging_event lst ON q.fst_event_id <> q.lst_event_id 
                                             AND q.lst_event_id = lst.event_id
             ORDER BY first_timestamp $sortDirection, q.kjoring_id $sortDirection) s
        WHERE s.row_number BETWEEN ? AND ?
    """.trimIndent()

    resourceScope {
        val conn = autoCloseable { dataSource.connection }
        val stmt = autoCloseable {
            conn.prepareStatement(sql).apply {
                setLong(1, fromEventId)
                setInt(2, page * pageSize)
                setInt(3, page.inc() * pageSize - 1)
            }
        }
        val rs = autoCloseable { stmt.executeQuery() }
        while (rs.next()) {
            val firstEventRef = ExecutionLogEventDTO.LogEventRef(
                rs.getLong(2),
                Instant.fromEpochMilliseconds(rs.getLong(3))
            )
            val lastEventRef = rs.getLong(4)
                .takeUnless { rs.wasNull() }
                ?.let { lastEventId ->
                    ExecutionLogEventDTO.LogEventRef(
                        lastEventId,
                        Instant.fromEpochMilliseconds(rs.getLong(5))
                    )
                }
            emit(ExecutionLogEventDTO(rs.getInt(1), firstEventRef, lastEventRef))
        }
    }

}.buffer(ROW_BUFFER_SIZE).flowOn(Dispatchers.IO)

private suspend fun fetchLogEventDTOs(
    dataSource: DataSource,
    kjoringId: Int,
    page: Int,
    pageSize: Short = DEFAULT_PAGE_SIZE
): Flow<LogEventDTO> = flow {
    pageSize.requireGtZero("pageSize")

    val (
        @Suppress("NAME_SHADOWING")
        page,
        sortDirection
    ) = pageAndSortOrder(page)

    val sql = """
        SELECT q.event_id, q.level_string, q.timestmp, q.logger_name, q.formatted_message
        FROM (SELECT row_number() OVER () - 1 row_number,
                     le.event_id,
                     le.level_string,
                     le.timestmp,
                     le.logger_name,
                     le.formatted_message
              FROM logging_event_property lep
                     JOIN logging_event le ON lep.event_id = le.event_id
              WHERE lep.mapped_key = 'kjoringId'
                AND lep.mapped_value = ?
              ORDER BY le.timestmp $sortDirection, le.event_id $sortDirection) q
        WHERE q.row_number BETWEEN ? AND ?
    """.trimIndent()

    resourceScope {
        val conn = autoCloseable { dataSource.connection }
        val stmt = autoCloseable {
            conn.prepareStatement(sql).apply {
                setString(1, kjoringId.toString())
                setInt(2, page * pageSize)
                setInt(3, page.inc() * pageSize - 1)
            }
        }
        val rs = autoCloseable { stmt.executeQuery() }
        while (rs.next()) {
            emit(
                LogEventDTO(
                    rs.getLong(1),
                    rs.getString(2),
                    Instant.fromEpochMilliseconds(rs.getLong(3)),
                    rs.getString(4),
                    rs.getString(5)
                )
            )
        }
    }
}.buffer(ROW_BUFFER_SIZE).flowOn(Dispatchers.IO)

private fun <T> Flow<T>.illegalArgumentToBadRequest(): Flow<T> = catch { cause ->
    when (cause) {
        is IllegalArgumentException -> throw BadRequestException(cause.message ?: "Bad request", cause)
        else -> throw cause
    }
}

private fun pageAndSortOrder(page: Int): Pair<Int, String> = when {
    page >= 0 && page < Int.MAX_VALUE -> page to "ASC"
    page == -1 -> 0 to "DESC"
    else -> throw IllegalArgumentException("page must be non-negative and strictly less than ${Int.MAX_VALUE} or exactly -1")
}

private fun Short.requireGtZero(variableName: String) {
    require(this > 0) { "$variableName must be strictly greater than 0" }
}

private val defaultStyle: String = """
    <style type="text/css">
      html, body {
        font-family: sans-serif;
        height: 100%;
      }
      thead {
        text-align: left;
        position: -webkit-sticky;
        position: sticky;
        pos: 0;
      }
      thead tr {
        background-color: #005900;
        color: white;
      }
      th.kjoring_id, tr.kjoring_id {
        width: 8em;
      }
      th.level. tr.level {
        width: 3em;
      }
      th.datetime, tr.datetime {
        width: 15em;
      };
    </style>
""".trimIndent()

private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS")
