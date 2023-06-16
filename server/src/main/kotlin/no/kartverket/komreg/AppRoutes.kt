package no.kartverket.komreg

import com.typesafe.config.ConfigFactory
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
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.transformation.*
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
                            Kommunenummer(kommune.kommunenr.toLong()),
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
                            Kommunenavn(it.kommunenavn),
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
    val kommunenavn: String,
    val kommunenr: String,
    val nyttKommunenummer: String,
    val skalOpprettes: Boolean? = false,
)

@Serializable
data class apiKommune(
    val kommunenavn: String,
    val kommunenr: Int,
)

@Serializable
data class apiFylke(
    val fylkesnavn: String,
    val fylkesnr: Int,
)

fun Application.routes() {
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.invalidateCaches()
            ConfigFactory.load("properties.conf")
        }
    }
    val kommuneService = KommuneServiceManager(bootContext).kommuneService

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
        route("/kommuner") {
            get {
                val kommunerFraMatrikkel = kommuneService.findAlleKommuner()
                val resultatKommuner = mutableListOf<apiKommune>()
                kommunerFraMatrikkel.forEach { kommuneFraMatrikkel ->
                    resultatKommuner.add(
                        apiKommune(
                            kommunenavn = kommuneFraMatrikkel.kommunenavn.name,
                            kommunenr = kommuneFraMatrikkel.kommunenummer.verdi().toInt(),
                        ),
                    )
                }
                call.respond(resultatKommuner)
            }
        }
        route("/fylker") {
            get {
                val fylkerFraMatrikkel = kommuneService.findAlleFylker()
                val resultatFylker = mutableListOf<apiFylke>()
                fylkerFraMatrikkel.forEach { fylkeFraMatrikkel ->
                    resultatFylker.add(
                        apiFylke(
                            fylkesnavn = fylkeFraMatrikkel.fylkesnavn.name,
                            fylkesnr = fylkeFraMatrikkel.fylkesnummer.value.toInt(),
                        ),
                    )
                }
                call.respond(resultatFylker)
            }
        }
    }
}
