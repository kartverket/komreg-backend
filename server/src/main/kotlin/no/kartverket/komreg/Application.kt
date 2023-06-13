package no.kartverket.komreg

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import no.kartverket.komreg.transformation.Config
import org.rocksdb.RocksDB
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ForkJoinPool

val logger: Logger = LoggerFactory.getLogger(object {}::class.java)

fun main(args: Array<String>) =
    ForkJoinPool.commonPool().execute {
        RocksDB.loadLibrary()
    }.also {
        io.ktor.server.netty.EngineMain.main(args)
    }

@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowHost("localhost:3000")
        allowHost("komreg.dev.skip.statkart.no", schemes = listOf("http", "https"))
        allowHeader(HttpHeaders.ContentType)
    }
    routes()

    logger.info("Source DB: ${Config.get("DB_MATRIKKEL_KILDE_USERNAME")}")
}
