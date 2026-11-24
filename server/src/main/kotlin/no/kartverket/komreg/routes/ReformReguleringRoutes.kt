package no.kartverket.komreg.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.komreg.repositories.ReformReguleringRepo
import no.kartverket.komreg.services.LagreReformResultat
import no.kartverket.komreg.services.ReguleringService
import no.kartverket.komreg.transformation.reform.ReformRegulering
import org.slf4j.LoggerFactory

/**
 * Lagring og henting av reglene ([no.kartverket.komreg.transformation.reform.ReformOp]) for en regulering som
 * kjøres med motoren `reform`. [json] må være `jsonSerializer()`, som kjenner identtyper og payloads.
 *
 * - `PUT /reform-reguleringer/{id}`: oppretter (201) eller erstatter (200). 400 ved ugyldig JSON eller regler
 *   som ikke består `ReformClaims.validate`; 409 hvis reguleringen allerede har en kjøring.
 * - `GET /reform-reguleringer/{id}`: 200 med reguleringen, eller 404.
 */
fun Application.reformReguleringRoutes(
    reguleringService: ReguleringService,
    reformReguleringRepo: ReformReguleringRepo,
    json: Json,
) {
    val logger = LoggerFactory.getLogger({}::class.java)

    routing {
        route("/reform-reguleringer/{id}") {
            put {
                val id = call.parameters["id"]!!
                val regulering = try {
                    json.decodeFromString(ReformRegulering.serializer(), call.receiveText())
                } catch (e: Exception) {
                    // Ukjente identtyper gir ikke alltid SerializationException.
                    logger.warn("Ugyldig reform-regulering $id", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ReformFeilDTO(listOf(ReformFeilDTO.Feil("UGYLDIG_JSON", e.message ?: e::class.simpleName.orEmpty()))),
                    )
                    return@put
                }

                when (val resultat = reguleringService.lagreReformRegulering(id, regulering)) {
                    is LagreReformResultat.Lagret -> {
                        logger.info("Lagret reform-regulering $id med ${regulering.regler.size} regler")
                        call.respond(if (resultat.opprettet) HttpStatusCode.Created else HttpStatusCode.OK)
                    }

                    is LagreReformResultat.Ugyldig -> call.respond(
                        HttpStatusCode.BadRequest,
                        ReformFeilDTO(resultat.feil.map { ReformFeilDTO.Feil(it::class.simpleName.orEmpty(), it.message) }),
                    )

                    LagreReformResultat.IBruk -> call.respond(
                        HttpStatusCode.Conflict,
                        "Reform-reguleringen $id har en kjøring og kan ikke endres",
                    )
                }
            }

            get {
                val id = call.parameters["id"]!!
                val regulering = reformReguleringRepo.get(id)
                if (regulering == null) {
                    call.respond(HttpStatusCode.NotFound, "Fant ingen reform-regulering med id $id")
                } else {
                    call.respondText(
                        json.encodeToString(ReformRegulering.serializer(), regulering),
                        ContentType.Application.Json,
                    )
                }
            }
        }
    }
}

@Serializable
data class ReformFeilDTO(val feil: List<Feil>) {
    @Serializable
    data class Feil(val kode: String, val melding: String)
}
