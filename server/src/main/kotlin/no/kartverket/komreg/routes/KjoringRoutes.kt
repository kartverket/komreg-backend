package no.kartverket.komreg.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.komreg.KjoringContextImpl
import no.kartverket.komreg.exceptions.MissingPathVariableException
import no.kartverket.komreg.exceptions.ReguleringAlreadyFinishedException
import no.kartverket.komreg.repositories.*
import no.kartverket.komreg.services.ReguleringService
import no.kartverket.komreg.services.transformEntities
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.SQLException
import javax.sql.DataSource

fun Application.kjoringroutes(
    transformationRepo: TransformationRepo,
    kjoringRepo: KjoringRepo,
    tilbakeføringsstatusRepo: TilbakeføringsstatusRepo,
    reguleringsService: ReguleringService,
    komregDataSource: DataSource,
) {
    val logger: Logger = LoggerFactory.getLogger({}::class.java)
    routing {
        route("/run/{regId}") {
            get {
                try {
                    val regId = call.parameters["regId"] ?: throw MissingPathVariableException("Missing regId")

                    val regulering = reguleringsService.getOrThrowRegulering(regId)

                    logger.info("Starter transformasjon for regulering: $regId")

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
                        logger.info("Gjenopptar kjøring med id: ${kjoringsomskalgjenopptas.id}, og regId: $regId")
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

                        logger.info("Starter ny kjøring med id: $kjoringId, og regId: $regId")
                        call.respond(HttpStatusCode.OK, "Starter ny kjøring med id: $kjoringId, og regId: $regId")
                    }
                } catch (e: Exception) {
                    when (e) {
                        is NotFoundException -> {
                            logger.error("Not found: ${e.message}")
                            call.respond(HttpStatusCode.NotFound, "Not found exception: ${e.message}")
                        }

                        is SQLException -> {
                            logger.error("SQL exception", e)
                            call.respond(HttpStatusCode.InternalServerError, "SQL exception: ${e.message}")
                        }

                        is ReguleringAlreadyFinishedException -> {
                            logger.error("${e::class.simpleName}: ${e.message}")
                            call.respond(HttpStatusCode.Conflict, "${e::class.simpleName}: ${e.message}")
                        }

                        else -> {
                            call.respond(HttpStatusCode.InternalServerError, "Internal server error: ${e.message}")
                            logger.error("Unexpected exception: $e")
                        }
                    }
                }
            }
        }
        route("/skjema") {
            post {
                try {
                    val requestBody = call.receiveText()
                    val mottakerSkjema = Json.decodeFromString(MottakerDTO.serializer(), requestBody)
                    kjoringRepo.settMottakerSkjema(mottakerSkjema.mottaker)


                    logger.info("Mottaker satt til: ${kjoringRepo.hentMottakerSkjema().mottaker}")
                    call.respond(HttpStatusCode.OK, "Mottaker satt til: ${kjoringRepo.hentMottakerSkjema().mottaker}")
                } catch (e: Exception) {
                    val msg = "Failed to save Mottaker skjema"
                    logger.error(msg, e)
                    call.respond(HttpStatusCode.InternalServerError, msg)
                }
            }
        }
        route("/is-running"){
            get{
                try {
                    val hasActiveKjoring = kjoringRepo.getKjoringer().any {
                        it.status == Kjoringstatus.KJØRER ||
                        it.status == Kjoringstatus.STARTET_TILBAKEFØRING ||
                        it.status == Kjoringstatus.IKKE_TILBAKEFØRT
                    }
                    call.respond(HttpStatusCode.OK, hasActiveKjoring)
                }
                catch (ex: Exception){
                    val msg = "Failed to get is-running status"
                    logger.error(msg, ex)
                    call.respond(HttpStatusCode.InternalServerError, msg)
                }
            }
        }
    }
}

@Serializable
data class MottakerDTO(
    val mottaker: Mottaker
)
