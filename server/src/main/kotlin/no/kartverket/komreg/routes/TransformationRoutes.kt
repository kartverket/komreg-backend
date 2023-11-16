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
import java.sql.SQLException

fun Application.transformationRoutes(
    transformationRepo: TransformationRepo,
    kjoringRepo: KjoringRepo,
    reguleringRepo: ReguleringRepo,
    tilbakeføringsstatusRepo: TilbakeføringsstatusRepo,
) {
    routing {
        route("/run/{regId}") {
            get {
                try {
                    val regId = call.parameters["regId"] ?: throw MissingPathVariableException("Missing regId")

                    val regulering = reguleringRepo.getReguleringById(regId)
                        ?: throw NotFoundException("Fant ingen regulering for regId: $regId")

                    call.application.log.info("Starter transformasjon for regulering: $regId")

                    val kjoringsomskalgjenopptas = kjoringRepo.finnStoppetKjøringForRegulering(regId)

                    if (kjoringsomskalgjenopptas != null) {
                        transformEntities(
                            regulering.toReguleringsinput(),
                            kjoringsomskalgjenopptas.id,
                            transformationRepo,
                            kjoringRepo,
                            tilbakeføringsstatusRepo,
                        )
                        call.application.log.info("Gjenopptar kjøring med id: ${kjoringsomskalgjenopptas.id}, og regId: $regId")

                        call.respond(
                            HttpStatusCode.OK,
                            "Gjenopptar kjøring med id: ${kjoringsomskalgjenopptas.id}, og regId: $regId",
                        )
                    } else {
                        val kjoringId = kjoringRepo.insertAndRetrieveKjoringId(regId)
                            ?: throw SQLException("Kunne ikke opprette kjøring for regId: $regId")
                        val reguleringsinput = regulering.toReguleringsinput()

                        transformEntities(
                            reguleringsinput,
                            kjoringId,
                            transformationRepo,
                            kjoringRepo,
                            tilbakeføringsstatusRepo,
                        )

                        call.application.log.info("Starter ny kjøring med id: $kjoringId, og regId: $regId")
                        call.respond(HttpStatusCode.OK, "Starter ny kjøring med id: $kjoringId, og regId: $regId")
                    }
                } catch (t: Exception) {
                    call.application.log.error("Feil under serialisering", t)
                    when (t) {
                        is NotFoundException -> call.respond(
                            HttpStatusCode.NotFound,
                            "Not found exception: ${t.message}",
                        )

                        is SQLException -> call.respond(
                            HttpStatusCode.InternalServerError,
                            "SQL exception: ${t.message}",
                        )

                        else -> call.respond(HttpStatusCode.InternalServerError, "Internal server error: ${t.message}")
                    }
                }
            }
        }
    }
}
