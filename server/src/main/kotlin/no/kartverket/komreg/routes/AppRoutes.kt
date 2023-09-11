package no.kartverket.komreg.routes

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
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.kartverket.komreg.*
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.verdi
import no.kartverket.komreg.transformation.KommuneServiceManager
import no.kartverket.komreg.transformation.transformEntities
import no.kartverket.komreg.transformation.transformStatuses
import java.util.Base64

fun Application.appRoutes(metricsRegistry: PrometheusMeterRegistry) {
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.invalidateCaches()
            ConfigFactory.load("properties.conf")
        }
    }
    val kommuneService = KommuneServiceManager(bootContext).kommuneService

    routing {
        route("/actuator/health") {
            get {
                call.respond("OK")
            }
        }

        route("/actuator/metrics") {
            get {
                call.respond(metricsRegistry.scrape())
            }
        }

        route("/run") {
            post {
                val regulering: Regulering = call.receive()
                val reguleringsinput = regulering.toReguleringsinput()

                call.application.log.info(regulering.toString())
                call.application.log.info(reguleringsinput.toString())

                transformEntities(reguleringsinput)

                call.respond("OK")
            }
        }

        route("/transform/status") {
            get {
                call.respond(transformStatuses)
            }
        }

        route("/fylker") {
            get {
                call.application.log.info("Fylker endpoint called")
                val fylkerFraMatrikkel = kommuneService.findAlleFylker()
                val fylker = mutableListOf<FylkeDTO>()
                fylkerFraMatrikkel
                    .filter { it.gyldigTilDato == null }
                    .forEach { fylkeFraMatrikkel ->
                        fylker.add(
                            FylkeDTO(
                                navn = fylkeFraMatrikkel.fylkesnavn.name,
                                fylkesnummer = fylkeFraMatrikkel.fylkesnummer.verdi(),
                            ),
                        )
                    }
                call.respond(fylker)
            }
        }

        route("/kommuner") {
            get {
                call.application.log.info("Kommuner endpoint called")
                val kommunerFraMatrikkel = kommuneService.findAlleKommuner()
                val kommuner = mutableListOf<KommuneDTO>()
                kommunerFraMatrikkel
                    .filter { it.gyldigTilDato == null }
                    .forEach { kommuneFraMatrikkel ->
                        kommuner.add(
                            KommuneDTO(
                                navn = kommuneFraMatrikkel.kommunenavn.name,
                                kommunenummer = kommuneFraMatrikkel.kommunenummer.verdi(),
                                gyldigTilDato = kommuneFraMatrikkel.gyldigTilDato,
                                koordinatsystem = kommuneFraMatrikkel.koordinatsystem,
                                senterpunkt = KoordinatDTO(
                                    x = kommuneFraMatrikkel.senterpunkt.x,
                                    y = kommuneFraMatrikkel.senterpunkt.y,
                                ),
                                nedsattKonsesjonsgrense = kommuneFraMatrikkel.nedsattKonsesjonsgrense,
                                godkjenteGardsnumre = godkjenteGardsnumreTilListe(kommuneFraMatrikkel.godkjenteGardsnumre),
                                adresse = kommuneFraMatrikkel.adresse?.let {
                                    AdresseDTO(
                                        adresselinje1 = it.adresselinje1,
                                        adresselinje2 = it.adresselinje2,
                                        postnummer = it.postnummer,
                                        poststed = it.poststed,
                                    )
                                },
                                standardRekvirent = kommuneFraMatrikkel.standardRekvirent?.let {
                                    StandardRekvirentDTO(it.orgnummer, it.navn)
                                },
                                kommunevapen = kommuneFraMatrikkel.kommunevapen?.let {
                                    Base64.getEncoder().encodeToString(it)
                                },
                            ),
                        )
                    }
                call.respond(kommuner)
            }
        }
    }
}
