package no.kartverket.komreg

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import no.kartverket.komreg.transformation.Transform
import org.rocksdb.RocksDB
import java.util.concurrent.ForkJoinPool

fun main(args: Array<String>) =
    ForkJoinPool.commonPool().execute {
        RocksDB.loadLibrary()
    }.also {
        io.ktor.server.netty.EngineMain.main(args)
    }

fun Application.module() {
    configureRouting()
    configureSerialization()
}

@Serializable
data class TransformedJson(val rule: String, val result: String)

@Serializable
data class ErrorsJson(val entry: String, val errors: List<String>)

@Serializable
data class Result(val transformed: List<TransformedJson>, val errors: List<ErrorsJson>)

fun Application.configureRouting() {
    routing {
        route("/test") {
            get {
                val data = executeRun()
                val errors = data
                    .filter { it is Transform.Transformed }
                    .filter { it.entity.data.log.size > 0 }
                    .map { ErrorsJson(it.entity.toString(), it.entity.data.log.map { it.toString() }) }

                val transforms = data
                    .filter { it is Transform.Transformed }
                    .filter { it.entity.data.log.size == 0 }
                    .map {
                        TransformedJson(
                            (it as Transform.Transformed).transformation.description,
                            it.entity.data.toString()
                        )
                    }

                call.respond(Result(transforms, errors))
            }
        }
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}

@Serializable
data class Test(val v: String)
