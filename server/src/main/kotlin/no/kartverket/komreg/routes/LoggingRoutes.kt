package no.kartverket.komreg.routes

import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.resourceScope
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
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
    inline val lasEventTime get() = lastEventRef?.eventId
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

private fun <T> Flow<T>.illegalArgumentToBadRequest(): Flow<T> = catch { cause ->
    when (cause) {
        is IllegalArgumentException -> throw BadRequestException(cause.message ?: "Bad request", cause)
        else -> throw cause
    }
}

fun Application.loggingRoutes(dataSource: DataSource) {
    routing {
        route("/log/api/executions/{executionId?}") {
            get {
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

private suspend fun fetchExecutionLogEventDTOs(
    dataSource: DataSource,
    fromEventId: Long,
    page: Int,
    pageSize: Short = DEFAULT_PAGE_SIZE
) : Flow<ExecutionLogEventDTO> = flow {
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
) : Flow<LogEventDTO> = flow {
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

private fun pageAndSortOrder(page: Int): Pair<Int, String> = when {
    page >= 0 && page < Int.MAX_VALUE -> page to "ASC"
    page == -1 -> 0 to "DESC"
    else -> throw IllegalArgumentException("page must be non-negative and strictly less than ${Int.MAX_VALUE} or exactly -1")
}

private fun Short.requireGtZero(variableName: String) {
    require(this > 0) { "$variableName must be strictly greater than 0" }
}
