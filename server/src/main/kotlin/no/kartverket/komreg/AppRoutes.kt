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
import no.kartverket.komreg.core.data.Transformed
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.transformation.*

val validationRules = ValidateRaw(
    rules = listOf(
        ::validMatrikkelNummer,
    ),
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
    val kommuneservice = KommuneService()
    val fylkeservice = FylkeService()
    routing {
        route("/run") {
            post {
                val input: List<TransformRuleForGardsnummer> = call.receive()
                val transformationRules = TransformationRules(
                    transformations = input.map {
                        TransformGardsnummer(it.from, it.to)
                    },
                )
                val result = executeSimpleRun(validationRules, transformationRules, transformationExecution)
                val responseData = result.mapNotNull {
                    when (it) {
                        is Transformed.Invalid -> null
                        is Transformed.Data -> it.data
                    }
                }.map {
                    ResponseMatrikel(it.gardsnummer.value)
                }

                call.respond(responseData)
            }
        }
        route("/actuator/health") {
            get {
                call.respond("OK")
            }
        }
        route("/kommuner") {
            get {
                call.respond(kommuneservice.getKommuner())
            }
        }
        route("/fylker") {
            get {
                call.respond(fylkeservice.getFylker())
            }
        }
    }
}
