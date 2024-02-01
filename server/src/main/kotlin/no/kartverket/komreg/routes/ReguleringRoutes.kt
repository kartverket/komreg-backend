package no.kartverket.komreg.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.services.ReguleringService

fun Application.reguleringRoutes(reguleringService: ReguleringService, reguleringRepo: ReguleringRepo) {
    routing {
        // Get all reguleringer
        route("/reguleringer") {
            get {
                val reguleringerList = reguleringRepo.getAllReguleringer()

                if (reguleringerList.isEmpty()) {
                    call.application.log.info("No reguleringer found.")
                }
                call.respond(reguleringerList)
            }
        }

        // Get regulering by id
        route("/reguleringer/{id}") {
            get {
                val regId = call.parameters["id"]

                val regulering = reguleringRepo.getReguleringById(regId ?: "")

                if (regulering != null) {
                    call.respond(regulering)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Regulering not found.")
                }
            }
        }

        // Create new regulering
        route("/reguleringer") {
            post {
                try {
                    val requestBody = call.receiveText()
                    val regulering: Regulering = Json.decodeFromString(Regulering.serializer(), requestBody)
                    reguleringRepo.insertRegulering(regulering)
                    call.respond(HttpStatusCode.OK, "Regulering JSON received and saved successfully.")
                } catch (e: Exception) {
                    application.log.error("${e.message}")
                    call.respond(HttpStatusCode.InternalServerError, "Failed to save Regulering.")
                }
            }
        }

        // Modify (replace) existing regulering
        route("/reguleringer") {
            put {
                val regulering: Regulering = call.receive()


                if (reguleringRepo.updateRegulering(regulering)) {
                    call.respond(HttpStatusCode.OK, "Regulering with ID ${regulering.id} updated successfully.")
                } else {
                    call.respond(HttpStatusCode.NotFound, "Regulering with ID ${regulering.id} not found.")
                }
            }
        }

        // Delete regulering by id
        route("/reguleringer/{regId}") {
            delete {
                val regId = call.parameters["regId"]

                try {
                    reguleringService.deleteReguleringById(regId!!)
                } catch (e: Exception) {
                    application.log.error("${e.message}")
                    call.respond(HttpStatusCode.InternalServerError, e.message.toString())
                }
            }
        }

        // Get endring by id in regulering by id
        route("/reguleringer/{regId}/endringer/{endrId}") {
            get {
                val regId = call.parameters["regId"]
                val endrId = call.parameters["endrId"]

                val endring = reguleringRepo.getEndringFromRegulering(regId!!, endrId!!)

                if (endring != null) {
                    call.respond(endring)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Endring or Regulering not found.")
                }
            }
        }

        // Get all endringer in regulering by id
        route("/reguleringer/{regId}/endringer") {
            get {
                val regId = call.parameters["regId"]

                val endringer = reguleringRepo.getAllEndringerFromRegulering(regId!!)

                if (endringer != null && endringer.isNotEmpty()) {
                    call.respond(endringer)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Endringer or Regulering not found.")
                }
            }
        }

        // Create new endring in regulering by reguleringId
        route("/reguleringer/{regId}/endringer") {
            post {
                val regId = call.parameters["regId"]
                val endringJson: String = call.receiveText()
                val endring = Json.decodeFromString<EndringDTO>(endringJson)

                val wasAdded = reguleringRepo.addEndringToRegulering(regId!!, endring)

                if (wasAdded) {
                    call.respond(HttpStatusCode.OK, "Endring added successfully.")
                } else {
                    call.respond(HttpStatusCode.NotFound, "Regulering not found.")
                }
            }
        }

        // Modify (replace) existing endring by id in regulering by id
        route("/reguleringer/{regId}/endringer/{endringId}") {
            put {
                val regId = call.parameters["regId"]
                val endringId = call.parameters["endringId"]
                val endringJson: String = call.receiveText()
                val updatedEndring = Json.decodeFromString<EndringDTO>(endringJson)

                val wasUpdated = reguleringRepo.updateEndringOfRegulering(regId!!, endringId!!, updatedEndring)

                if (wasUpdated) {
                    call.respond(HttpStatusCode.OK, "Endring updated successfully.")
                } else {
                    call.respond(HttpStatusCode.NotFound, "Regulering or Endring not found.")
                }
            }
        }

        // Delete existing endring by id in regulering by id
        route("/reguleringer/{regId}/endringer/{endringId}") {
            delete {
                val regId = call.parameters["regId"]
                val endringId = call.parameters["endringId"]

                val wasDeleted = reguleringRepo.deleteEndringFromRegulering(regId!!, endringId!!)

                if (wasDeleted) {
                    call.respond(HttpStatusCode.OK, "Endring deleted successfully.")
                } else {
                    call.respond(HttpStatusCode.NotFound, "Regulering or Endring not found.")
                }
            }
        }

        // Get all kommunedata in endring by id in regulering by id
        route("/reguleringer/{regId}/endringer/{endringId}/kommunedata") {
            post {

                val regId = call.parameters["regId"]
                val endringId = call.parameters["endringId"]
                println("POST /reguleringer/$regId/endringer/$endringId/kommunedata")

                val dto = call.receiveText()
                val oppdatertKommune = Json.decodeFromString<OppdaterKommuneDTO>(dto)

                println(dto)

                if (regId != null && endringId != null) {
                    val kommuneDTO = reguleringRepo.getNyKommuneFromEndring(
                        regId,
                        endringId,
                        oppdatertKommune.fylkesnummer,
                        oppdatertKommune.kommunenummer
                    )
                    if (kommuneDTO != null) {
                        call.respond(kommuneDTO)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "KommuneDTO not found")
                    }
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Missing regId and/or endringId")
                }
            }
        }

        route("/reguleringer/{regId}/endringer/{endringId}/kommunedata") {
            put {
                val regId = call.parameters["regId"]
                val endringId = call.parameters["endringId"]
                val dto = call.receiveText()
                val oppdatertKommuneDTO = Json.decodeFromString<OppdaterKommuneDTO>(dto)

                println(oppdatertKommuneDTO)

                val tidligereEndring = reguleringRepo.getEndringFromRegulering(regId!!, endringId!!)
                    ?: throw IllegalArgumentException("KommuneDTO not found")

                val nyKommuneSomSkalOppdaters =
                    tidligereEndring.nyeKommuner.firstOrNull { it.fylkesnummer == oppdatertKommuneDTO.fylkesnummer && it.kommunenummer == oppdatertKommuneDTO.kommunenummer }
                        ?: throw IllegalArgumentException("KommuneDTO not found")

                val oppdatertKommune = KommuneDTO(
                    navn = nyKommuneSomSkalOppdaters.navn,
                    fylkesnummer = nyKommuneSomSkalOppdaters.fylkesnummer,
                    kommunenummer = nyKommuneSomSkalOppdaters.kommunenummer,
                    gyldigTilDato = nyKommuneSomSkalOppdaters.gyldigTilDato,
                    koordinatsystem = oppdatertKommuneDTO.koordinatsystem ?: nyKommuneSomSkalOppdaters.koordinatsystem,
                    senterpunkt = oppdatertKommuneDTO.senterpunkt ?: nyKommuneSomSkalOppdaters.senterpunkt,
                    nedsattKonsesjonsgrense = oppdatertKommuneDTO.nedsattKonsesjonsgrense
                        ?: nyKommuneSomSkalOppdaters.nedsattKonsesjonsgrense,
                    godkjenteGardsnumre = oppdatertKommuneDTO.godkjenteGardsnumre
                        ?: nyKommuneSomSkalOppdaters.godkjenteGardsnumre,
                    adresse = oppdatertKommuneDTO.adresse ?: nyKommuneSomSkalOppdaters.adresse,
                    standardRekvirent = oppdatertKommuneDTO.standardRekvirent
                        ?: nyKommuneSomSkalOppdaters.standardRekvirent,
                    kommunevapen = oppdatertKommuneDTO.kommunevapen ?: nyKommuneSomSkalOppdaters.kommunevapen,

                    )
                reguleringRepo.updateEndringOfRegulering(
                    regId,
                    endringId,
                    tidligereEndring.copy(nyeKommuner = tidligereEndring.nyeKommuner.map { if (it == nyKommuneSomSkalOppdaters) oppdatertKommune else it })
                )

                call.respond(HttpStatusCode.OK, "KommuneDTO updated successfully")

            }
        }


    }
}
