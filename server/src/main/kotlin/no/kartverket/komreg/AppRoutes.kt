package no.kartverket.komreg

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import no.kartverket.komreg.transformation.executeSimpleRun

@Serializable
data class TransformRuleForGardsnummer(
    val from: Int,
    val to: Int,
)

fun Application.routes() {
    routing {
        route("/run") {
            post {
                val input: List<TransformRuleForGardsnummer> = call.receive()
                val result = executeSimpleRun()
                call.respond(result)
            }
        }
        route("/actuator/health") {
            get {
                call.respond("OK")
            }
        }
    }
}
