package no.kartverket.komreg

import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
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
    routes()
}
