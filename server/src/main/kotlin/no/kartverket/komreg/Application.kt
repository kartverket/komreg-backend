package no.kartverket.komreg

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.kartverket.komreg.integration.SchemaManager
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.repositories.TilbakeføringsstatusRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.routes.*
import no.kartverket.komreg.services.KjoringService
import no.kartverket.komreg.services.ReguleringService
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

var mottakerUsername: String? = null
var mottakerPassword: String? = null

fun main(args: Array<String>) =
    ForkJoinPool.commonPool().execute {
        RocksDB.loadLibrary()
    }.also {
        EngineMain.main(args)
    }

@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    val metricsRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val komregJdbcUrl = env["DB_KOMREG_JDBC_URL"]
    val komregDbUsername = env["DB_KOMREG_USERNAME"]
    val komregDbPassword = env["DB_KOMREG_PASSWORD"]

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

    val kjoringRepo = KjoringRepo(komregDbPool)
    val reguleringsRepo = ReguleringRepo(komregDbPool)
    val tilbakeføringsstatusRepo = TilbakeføringsstatusRepo(komregDbPool)
    val transformationRepo = TransformationRepo(komregDbPool, jsonSerializer())
    val reguleringService = ReguleringService(reguleringsRepo, kjoringRepo)
    val kjoringService = KjoringService(kjoringRepo)
    val schemaManager = SchemaManager(kjoringRepo)

    mottakerUsername = schemaManager.getMottakerUsername()
    mottakerPassword = schemaManager.getMottakerPassword()

    val matrikkelDbUsername = env[mottakerUsername]


    install(MicrometerMetrics) {
        registry = metricsRegistry
    }

    logger.info("Current environment: ${System.getenv("environment")}")
    logger.info("Mottaker DB: $matrikkelDbUsername")

    environment.monitor.subscribe(ApplicationStopping) {
        kjoringService.handleShutdown()
    }

    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowHost("localhost:3000")
        allowHost("komreg.dev.skip.statkart.no", schemes = listOf("http", "https"))
        allowHost("komreg.test.skip.statkart.no", schemes = listOf("http", "https"))
        allowHost("komreg.prod.skip.statkart.no", schemes = listOf("http", "https"))
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Put)
    }

    // TODO: PoC for uthenting av fordelingsparametre for kommune
    fun createMatrikkelDataSource(): HikariDataSource {
        val hikariConfig = HikariConfig()
        hikariConfig.poolName = "db-connection"
        hikariConfig.driverClassName = "oracle.jdbc.OracleDriver"
        hikariConfig.jdbcUrl = env["DB_MATRIKKEL_JDBC_URL"]
        hikariConfig.username = env[mottakerUsername]
        hikariConfig.password = env[mottakerPassword]
        return HikariDataSource(hikariConfig)
    }

    internalRoutes(metricsRegistry)
    reguleringRoutes(reguleringService, reguleringsRepo)
    kjoringroutes(transformationRepo, kjoringRepo, tilbakeføringsstatusRepo, reguleringService, komregDbPool)
    grunndataRoutes(createMatrikkelDataSource())
    stedsnavnRoutes(reguleringsRepo, kjoringRepo, transformationRepo)

    enableFagLogging(komregDbPool)
}
