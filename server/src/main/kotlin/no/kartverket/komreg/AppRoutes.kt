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
import no.kartverket.komreg.core.domain.Fylkesnavn
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenavn
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.transformation.Kommuneendring
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.transformEntities
import no.kartverket.komreg.transformation.transformStatuses
import java.time.LocalDate
import no.kartverket.komreg.core.domain.Fylke as NyttFylke
import no.kartverket.komreg.core.domain.Kommune as NyKommune

@Serializable
data class Regulering(
    val id: String,
    val navn: String,
    val dato: String, // Date?
    val endringer: List<Fylkesdeling>, // Endringer som sealed classes og diskriminator?
) {
    fun toReguleringsinput(): Reguleringsinput {
        return Reguleringsinput(
            id,
            LocalDate.parse(dato),
            endringer = endringer.flatMap { fylkesdeling ->
                fylkesdeling.nyeFylker.flatMap { fylke ->
                    fylke.kommuner.map { kommune ->
                        Kommuneendring(
                            Kommunenummer(kommune.kommunenummer.toLong()),
                            Kommunenummer(kommune.nyttKommunenummer.toLong()),
                        )
                    }
                }
            },
            fylker = endringer.flatMap { fylkesdeling ->
                fylkesdeling.nyeFylker.filter { it.skalOpprettes == true }.map {
                    NyttFylke(
                        Fylkesnummer(it.fylkesnummer.toLong()),
                        Fylkesnavn(it.navn),
                    )
                }
            },
            kommuner = endringer.flatMap { fylkesdeling ->
                fylkesdeling.nyeFylker.flatMap { fylke ->
                    fylke.kommuner.filter { it.skalOpprettes == true }.map {
                        NyKommune(
                            Kommunenummer(it.nyttKommunenummer.toLong()),
                            Kommunenavn(it.navn),
                        )
                    }
                }
            },
        )
    }
}

@Serializable
data class Fylkesdeling(
    val id: String,
    val navn: String,
    val type: String,
    val gammeltFylke: Fylke,
    val nyeFylker: List<Fylke>,
)

@Serializable
data class Fylke(
    val navn: String,
    val fylkesnummer: String,
    val kommuner: List<Kommune>,
    val skalOpprettes: Boolean? = false,
)

@Serializable
data class Kommune(
    val navn: String,
    val kommunenummer: String,
    val nyttKommunenummer: String,
    val skalOpprettes: Boolean? = false,
)

fun Application.routes() {
    routing {
        route("/run") {
            post {
                val regulering: Regulering = call.receive()
                call.application.log.info(regulering.toString())
                call.application.log.info(regulering.toReguleringsinput().toString())

                transformEntities(regulering.toReguleringsinput())

                call.respond("OK")
            }
        }
        route("/actuator/health") {
            get {
                call.respond("OK")
            }
        }
        route("/transform/status") {
            get {
                call.respond(transformStatuses)
            }
        }
    }
}
