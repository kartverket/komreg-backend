package no.kartverket.komreg

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import org.rocksdb.RocksDB
import java.util.concurrent.ForkJoinPool

fun main(args: Array<String>) =
    ForkJoinPool.commonPool().execute {
        RocksDB.loadLibrary()
    }.also {
        io.ktor.server.netty.EngineMain.main(args)
    }

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(MicrometerMetrics)
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowHost("localhost:3000")
        allowHost("statkart.no", schemes = listOf("http", "https"), subDomains = listOf("*"))
        allowHeader(HttpHeaders.ContentType)
    }
    routes()
}
