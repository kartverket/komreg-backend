package no.kartverket.komreg.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import no.kartverket.komreg.repositories.ReguleringRepo

fun Application.reguleringRoutes(reguleringRepo: ReguleringRepo) {
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
                val regulering: Regulering = call.receive()

                try {
                    reguleringRepo.insertRegulering(regulering)
                    call.respond(HttpStatusCode.OK, "Regulering JSON received and saved successfully.")
                } catch (e: Exception) {
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

                if (reguleringRepo.deleteReguleringById(regId!!)) {
                    call.respond(HttpStatusCode.OK, "Regulering with ID $regId deleted successfully.")
                } else {
                    call.respond(HttpStatusCode.NotFound, "Regulering with ID $regId not found.")
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
    }
}
