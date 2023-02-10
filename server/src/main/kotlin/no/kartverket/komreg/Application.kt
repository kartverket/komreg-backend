package no.kartverket.komreg

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
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
    routes()
}
