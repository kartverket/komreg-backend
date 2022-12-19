package no.kartverket.komreg.api

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import org.rocksdb.RocksDB
import java.time.Duration
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
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    configureRouting()
}
