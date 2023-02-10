package no.kartverket.komreg

import io.ktor.client.HttpClient
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.data.TransformedData
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.transformation.TransformGardsnummer
import no.kartverket.komreg.transformation.TransformationExecution
import no.kartverket.komreg.transformation.TransformationRules
import no.kartverket.komreg.transformation.ValidateRaw
import no.kartverket.komreg.transformation.executeSimpleRun
import no.kartverket.komreg.transformation.validMatrikkelNummer
import org.rocksdb.RocksDB
import java.util.concurrent.ForkJoinPool
import io.ktor.client.plugins.websocket.WebSockets as WebSocketClientPlugin

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
    val wsclient = HttpClient {
        install(WebSocketClientPlugin)
    }
    routes()
}

val validationRules = ValidateRaw(
    rules = listOf(
        ::validMatrikkelNummer
    )
)
val transformationExecution = TransformationExecution<Matrikkelnummer>()

@Serializable
data class TransformRuleForGardsnummer(
    val from: Int,
    val to: Int,
)

@Serializable
data class ResponseMatrikel(val gardsnummer: Int)

fun Application.routes() {
    routing {
        route("/run") {
            post {
                val input: List<TransformRuleForGardsnummer> = call.receive()
                val transformationRules = TransformationRules(
                    transformations = input.map {
                        TransformGardsnummer(it.from, it.to)
                    }
                )
                val result = executeSimpleRun(validationRules, transformationRules, transformationExecution)
                val result2 = result.mapNotNull {
                    when (it) {
                        is TransformedData.Invalid -> null
                        is TransformedData.Valid -> it.data
                    }
                }.map {
                    ResponseMatrikel(it.gardsnummer.value)
                }

                call.respond(result2)
            }
        }
    }
}
