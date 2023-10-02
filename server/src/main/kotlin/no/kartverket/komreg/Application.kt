package no.kartverket.komreg

import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
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

    val reguleringsRepo = ReguleringRepo(komregJdbcUrl, komregDbUsername, komregDbPassword)
    val transformationRepo = TransformationRepo(komregJdbcUrl, komregDbUsername, komregDbPassword, jsonSerializer())
    val kjoringRepo = KjoringRepo(komregJdbcUrl, komregDbUsername, komregDbPassword)

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

    internalRoutes(metricsRegistry)
    reguleringRoutes(reguleringsRepo)
    transformationRoutes(transformationRepo, kjoringRepo)
    grunndataRoutes()
}
