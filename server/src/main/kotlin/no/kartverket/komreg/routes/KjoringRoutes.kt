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
import no.kartverket.komreg.exceptions.KjoringAlreadyFinishedException
import no.kartverket.komreg.exceptions.MissingPathVariableException
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.Kjoringstatus
import no.kartverket.komreg.repositories.TilbakeføringsstatusRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.services.KjoringService
import no.kartverket.komreg.services.ReguleringService
import no.kartverket.komreg.services.transformEntities
import java.sql.SQLException
import javax.sql.DataSource

fun Application.kjoringRoutes(
    kjoringService: KjoringService,
    transformationRepo: TransformationRepo,
    tilbakeforingsstatusRepo: TilbakeføringsstatusRepo,
    reguleringsService: ReguleringService,
    kjoringRepo: KjoringRepo,
    komregDataSource: DataSource,
) {
    routing {
        route("/kjoring/{regId}") {
            get {
                val regId = call.parameters["regId"] ?: return@get call.respond(HttpStatusCode.BadRequest)

                try {
                    val kjoring = kjoringService.opprettKjoring(regId)
                    call.respond(
                        HttpStatusCode.OK,
                        "Opprettet kjøring med id: ${kjoring.id} for regulering: $regId. Denne kjøringen bruker skjema ${kjoring.skjema}",
                    )
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.message.toString())
                }
            }
        }
        route("/run/{kjoringId}") {
            get {
                try {
                    val kjoringId =
                        call.parameters["kjoringId"] ?: throw MissingPathVariableException("Mangler kjoringId")

                    val kjoring = kjoringService.hentKjoring(kjoringId.toInt())
                        ?: throw NotFoundException("Kjøring med id: $kjoringId finnes ikke")

                    val erForstegangskjoring = kjoring.status != Kjoringstatus.STOPPET

                    if (kjoring.status == Kjoringstatus.FERDIG) {
                        throw KjoringAlreadyFinishedException("Kan ikke starte kjøring som allerede er ferdig")
                    }

                    val regulering = reguleringsService.getOrThrowRegulering(kjoring.regulering)

                    if (!erForstegangskjoring) {
                        call.application.log.info("Gjennopptar kjøring med id: ${kjoring.id} og regulering: ${regulering.id}")
                    }

                    call.application.log.info("Starter transformasjon for regulering: ${regulering.id}")

                    kjoringService.startKjoring(kjoring.id)

                    transformEntities(
                        regulering.toReguleringsinput(),
                        KjoringContextImpl(kjoring.id, komregDataSource),
                        transformationRepo,
                        kjoringRepo,
                        tilbakeforingsstatusRepo,
                        erForstegangskjoring,
                    )
                } catch (e: Exception) {
                    call.application.log.error("Feil under start av kjoring", e)
                    when (e) {
                        is NotFoundException -> call.respond(
                            HttpStatusCode.NotFound,
                            "${e::class.simpleName}: ${e.message}",
                        )

                        is SQLException -> call.respond(
                            HttpStatusCode.InternalServerError,
                            "${e::class.simpleName}: ${e.message}",
                        )

                        is KjoringAlreadyFinishedException -> call.respond(
                            HttpStatusCode.Conflict,
                            "${e::class.simpleName}: ${e.message}",
                        )

                        else -> call.respond(HttpStatusCode.InternalServerError, "Internal server error: ${e.message}")
                    }
                }
            }
        }
    }
}
