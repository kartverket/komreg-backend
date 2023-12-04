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
import no.kartverket.komreg.KjoringContextImpl
import no.kartverket.komreg.exceptions.MissingPathVariableException
import no.kartverket.komreg.exceptions.ReguleringAlreadyFinishedException
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.Kjoringstatus
import no.kartverket.komreg.repositories.TilbakeføringsstatusRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.services.ReguleringService
import no.kartverket.komreg.services.transformEntities
import java.sql.SQLException
import javax.sql.DataSource

fun Application.transformationRoutes(
    transformationRepo: TransformationRepo,
    kjoringRepo: KjoringRepo,
    tilbakeføringsstatusRepo: TilbakeføringsstatusRepo,
    reguleringsService: ReguleringService,
    komregDataSource: DataSource,
) {
    routing {
        route("/run/{regId}") {
            get {
                try {
                    val regId = call.parameters["regId"] ?: throw MissingPathVariableException("Missing regId")

                    val regulering = reguleringsService.getOrThrowRegulering(regId)

                    call.application.log.info("Starter transformasjon for regulering: $regId")

                    val kjoringsomskalgjenopptas = kjoringRepo.finnStoppetKjøringForRegulering(regId)

                    if (kjoringsomskalgjenopptas != null) {
                        transformEntities(
                            regulering.toReguleringsinput(),
                            KjoringContextImpl(kjoringsomskalgjenopptas.id, komregDataSource),
                            transformationRepo,
                            kjoringRepo,
                            tilbakeføringsstatusRepo,
                            false,
                        )
                        call.application.log.info("Gjenopptar kjøring med id: ${kjoringsomskalgjenopptas.id}, og regId: $regId")
                        kjoringRepo.setStatusForKjøring(kjoringsomskalgjenopptas.id, Kjoringstatus.KJØRER)

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
                            KjoringContextImpl(kjoringId, komregDataSource),
                            transformationRepo,
                            kjoringRepo,
                            tilbakeføringsstatusRepo,
                            true,
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

                        is ReguleringAlreadyFinishedException -> call.respond(
                            HttpStatusCode.Conflict,
                            "${t::class.simpleName}: ${t.message}",
                        )

                        else -> call.respond(HttpStatusCode.InternalServerError, "Internal server error: ${t.message}")
                    }
                }
            }
        }
    }
}
