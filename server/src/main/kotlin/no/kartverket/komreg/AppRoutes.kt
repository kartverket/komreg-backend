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
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.verdi
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.transformation.Kommuneendring
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.transformEntities

@Serializable
data class Regulering(
    val id: String,
    val navn: String,
    val dato: String, // Date?
    val endringer: List<Fylkesdeling>, // Endringer som sealed classes og diskriminator?
) {
    fun toReguleringsinput(): Reguleringsinput {
        return Reguleringsinput(
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
)

@Serializable
data class Kommune(
    val navn: String,
    val kommunenummer: String,
    val nyttKommunenummer: String,
)

@Serializable
data class Transformasjonsresultat(
    val id: String,
    val fra: String,
    val til: String,
)

fun Application.routes() {
    routing {
        route("/run") {
            post {
                val regulering: Regulering = call.receive()
                call.application.log.info(regulering.toReguleringsinput().toString())
                val result = transformEntities(regulering.toReguleringsinput())
                call.respond(
                    result.map {
                        Transformasjonsresultat(
                            id = it.id,
                            fra = Kommunenummer(
                                (it.sourceObject as Entity).identOf(),
                                (it.sourceObject as Entity).identOf(),
                            ).verdi(),
                            til = Kommunenummer(
                                it.transformedIdent[Fylkesnummer::class] as Fylkesnummer,
                                it.transformedIdent[Kommunenummer.Lopenummer::class] as Kommunenummer.Lopenummer,
                            ).verdi(),
                        )
                    },
                )
            }
        }
        route("/actuator/health") {
            get {
                call.respond("OK")
            }
        }
    }
}
