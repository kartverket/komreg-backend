package no.kartverket.komreg

import arrow.core.raise.result
import arrow.core.raise.zipOrAccumulate
import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.db.DBAppender
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.db.DataSourceConnectionSource
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.kartverket.komreg.core.logging.FAG
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.routes.grunndataRoutes
import no.kartverket.komreg.routes.internalRoutes
import no.kartverket.komreg.routes.reguleringRoutes
import no.kartverket.komreg.routes.transformationRoutes
import org.flywaydb.core.Flyway
import org.rocksdb.RocksDB
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ForkJoinPool
import javax.sql.DataSource

val env = dotenv {
    ignoreIfMissing = true
    systemProperties = true
}

val logger: Logger = LoggerFactory.getLogger(object {}::class.java)

fun main(args: Array<String>) =
    ForkJoinPool.commonPool().execute {
        RocksDB.loadLibrary()
    }.also {
        EngineMain.main(args)
    }

@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    logger.info("Current environment: ${System.getenv("environment")}")
    logger.info("Source DB: ${env["DB_MATRIKKEL_KILDE_USERNAME"]}, Mottaker DB: ${env["DB_MATRIKKEL_MOTTAKER_USERNAME"]}")

    val metricsRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        registry = metricsRegistry
    }

    val komregJdbcUrl = env["DB_KOMREG_JDBC_URL"]
    val komregDbUsername = env["DB_KOMREG_USERNAME"]
    val komregDbPassword = env["DB_KOMREG_PASSWORD"]

    if (!komregJdbcUrl.isNullOrEmpty()) {
        val flyway = Flyway.configure()
            .schemas("komreg")
            .dataSource(
                komregJdbcUrl,
                komregDbUsername,
                komregDbPassword,
            )
            .load()

        flyway.migrate()
    }

    val komregDbPool = run {
        val hikariConfig = HikariConfig()
        hikariConfig.poolName = "komreg-db-connection"
        hikariConfig.jdbcUrl = komregJdbcUrl
        hikariConfig.username = komregDbUsername
        hikariConfig.password = komregDbPassword
        hikariConfig.minimumIdle = 1
        hikariConfig.keepaliveTime = 600000
        HikariDataSource(hikariConfig)
    }

    enableFagLogging(komregDbPool)

    val reguleringsRepo = ReguleringRepo(komregDbPool)
    val kjoringRepo = KjoringRepo(komregDbPool)
    val transformationRepo = TransformationRepo(komregDbPool, jsonSerializer())

    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowHost("localhost:3000")
        allowHost("komreg.dev.skip.statkart.no", schemes = listOf("http", "https"))
        allowHost("komreg.test.skip.statkart.no", schemes = listOf("http", "https"))
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Put)
    }

    // TODO: PoC for uthenting av fordelingsparametre for kommune
    fun createKildeDataSource(): HikariDataSource {
        val hikariConfig = HikariConfig()
        hikariConfig.poolName = "db-connection"
        hikariConfig.driverClassName = "oracle.jdbc.OracleDriver"
        hikariConfig.jdbcUrl = env["DB_MATRIKKEL_JDBC_URL"]
        hikariConfig.username = env["DB_MATRIKKEL_KILDE_USERNAME"]
        hikariConfig.password = env["DB_MATRIKKEL_KILDE_PASSWORD"]
        return HikariDataSource(hikariConfig)
    }

    internalRoutes(metricsRegistry)
    reguleringRoutes(reguleringsRepo)
    transformationRoutes(transformationRepo, kjoringRepo, reguleringsRepo)
    grunndataRoutes(createKildeDataSource())
}

private fun Application.enableFagLogging(komregDbPool: DataSource) {
    when (val loggerContext = LoggerFactory.getILoggerFactory()) {
        is LoggerContext -> {
            val dbAppender = DBAppender().apply {
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
}
