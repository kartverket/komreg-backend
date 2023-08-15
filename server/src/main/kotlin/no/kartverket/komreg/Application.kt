package no.kartverket.komreg

import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.*
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
        io.ktor.server.netty.EngineMain.main(args)
    }

@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    logger.info("Current environment: ${System.getenv("environment")}")
    logger.info("Source DB: ${env["DB_MATRIKKEL_KILDE_USERNAME"]}, Mottaker DB: ${env["DB_MATRIKKEL_MOTTAKER_USERNAME"]}")

    val metricsRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        registry = metricsRegistry
    }
    val flyway = Flyway.configure()
        .dataSource(
            env["DB_TRANSFORMATION_JDBC_URL"],
            env["DB_TRANSFORMATION_USERNAME"],
            env["DB_TRANSFORMATION_PASSWORD"],
        )
        .load()

    flyway.migrate()

    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowHost("localhost:3000")
        allowHost("komreg.dev.skip.statkart.no", schemes = listOf("http", "https"))
        allowHost("komreg.test.skip.statkart.no", schemes = listOf("http", "https"))
        allowHeader(HttpHeaders.ContentType)
    }

    routes(metricsRegistry)
}
