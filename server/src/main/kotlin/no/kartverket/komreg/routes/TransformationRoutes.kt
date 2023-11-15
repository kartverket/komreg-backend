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
import no.kartverket.komreg.exceptions.MissingPathVariableException
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.repositories.TilbakeføringsstatusRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.services.transformEntities

fun Application.transformationRoutes(
    transformationRepo: TransformationRepo,
    kjoringRepo: KjoringRepo,
    reguleringRepo: ReguleringRepo,
    configRepo: TilbakeføringsstatusRepo,
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

                        transformEntities(reguleringsinput, kjoringId, transformationRepo, kjoringRepo, configRepo)

                        call.respond("OK")
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to insert into kjoring table.")
                    }
                } catch (t: Exception) {
                    call.application.log.error("Feil under serialisering", t)
                    when (t) {
                        is NotFoundException -> call.respond(
                            HttpStatusCode.NotFound,
                            "Not found exception: ${t.message}",
                        )

                        else -> call.respond(HttpStatusCode.InternalServerError, "Internal server error: ${t.message}")
                    }
                }
            }
        }
    }
}
