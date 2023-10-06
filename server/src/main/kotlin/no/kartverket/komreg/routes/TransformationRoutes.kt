package no.kartverket.komreg.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.services.transformEntities
import no.kartverket.komreg.services.transformStatuses
import no.kartverket.komreg.validators.exceptions.MissingPathVariableException

fun Application.transformationRoutes(
    transformationRepo: TransformationRepo,
    kjoringRepo: KjoringRepo,
    reguleringRepo: ReguleringRepo,
) {
    routing {
        route("/run/{regId}") {
            get {
                try {
                    val regId = call.parameters["regId"] ?: throw MissingPathVariableException("Missing regId")

                    val regulering = reguleringRepo.getReguleringById(regId)
                        ?: throw NotFoundException("Fant ingen regulering for regId: $regId")

                    call.application.log.info("Starter transformasjon for regulering: $regId")

                    val kjoringId = kjoringRepo.insertAndRetrieveKjoringId(regId)
                    if (kjoringId != null) {
                        val reguleringsinput = regulering.toReguleringsinput()

                        transformEntities(reguleringsinput, kjoringId, transformationRepo, kjoringRepo)

                        call.respond("OK")
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to insert into kjoring table.")
                    }
                } catch (t: Throwable) {
                    call.application.log.error("Feil under serialisering", t)
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
        }

        route("/transform/status") {
            get {
                call.respond(transformStatuses)
            }
        }
    }
}
