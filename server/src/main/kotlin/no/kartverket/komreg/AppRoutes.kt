package no.kartverket.komreg

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.transformation.Kommuneendring
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.executeSimpleRun

@Serializable
data class Regulering(
    val id: String,
    val navn: String,
    val dato: String, // Date?
    val fylkesendring: Fylkesendring, // Flertall? Entall? WAT?
) {
    fun toReguleringsinput(): Reguleringsinput {
        return Reguleringsinput(
            endringer = fylkesendring.fylkesdelinger.flatMap { fylkesdeling ->
                fylkesdeling.nyeFylker.flatMap { fylke ->
                    fylke.kommuner.map { kommune ->
                        Kommuneendring(
                            Kommunenummer(kommune.kommunenummer.toLong()),
                            Kommunenummer(kommune.nyttKommunenummer.toLong())
                        )
                    }
                }
            }
        )
    }
}

@Serializable
data class Fylkesendring(
    val fylkesdelinger: List<Fylkesdeling>,
)

@Serializable
data class Fylkesdeling(
    val id: String,
    val navn: String,
    val gammeltFylke: Fylke,
    val nyeFylker: List<Fylke>,
)

@Serializable
data class Fylke(
    val navn: String,
    val fylkesnummer: String, // Fylkesnummer?
    val kommuner: List<Kommune>, // Eh? Er jo null for gammeltFylke
)

@Serializable
data class Kommune(
    val navn: String,
    val kommunenummer: String, // Kommunenummer?
    val nyttKommunenummer: String,
)

fun Application.routes() {
    routing {
        route("/run") {
            post {
                val regulering: Regulering = call.receive()
                call.application.log.info(regulering.toReguleringsinput().toString())
                val result = executeSimpleRun(regulering.toReguleringsinput())
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
