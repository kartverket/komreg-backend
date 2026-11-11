package no.kartverket.komreg.regulering.api.server.route.regulering

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.komreg.regulering.api.server.persistence.ReformSpecRepository
import no.kartverket.komreg.regulering.api.server.service.reform.ReformSpecService
import no.kartverket.komreg.regulering.api.server.service.reform.ValideringResultat
import no.kartverket.komreg.regulering.api.server.service.reform.parseReformSpec
import no.kartverket.komreg.regulering.reform.format.ReformSpec

@Serializable
data class OpprettetRegulering(val id: Int)

/** Endepunktene for specer under `/api/reguleringer`. Se `docs/reform-format.md`. */
fun Route.reguleringer(appJson: Json, repository: ReformSpecRepository, service: ReformSpecService) {
    /** Leser og validerer specen i body. Svarer selv og gir `null` hvis den er ugyldig. */
    suspend fun RoutingCall.validSpec(): ReformSpec? {
        val spec = when (val parsed = appJson.parseReformSpec(receiveText())) {
            is Either.Left -> {
                respond(HttpStatusCode.UnprocessableEntity, ValideringResultat.ugyldig(listOf(parsed.value)))
                return null
            }

            is Either.Right -> parsed.value
        }
        val feil = service.validate(spec)
        if (feil.isNotEmpty()) {
            respond(HttpStatusCode.UnprocessableEntity, ValideringResultat.ugyldig(feil))
            return null
        }
        return spec
    }

    suspend fun RoutingCall.id(): Int? =
        parameters["id"]?.toIntOrNull().also { if (it == null) respond(HttpStatusCode.BadRequest, "Ugyldig id") }

    route("/api/reguleringer") {
        post("/validering") {
            val spec = when (val parsed = appJson.parseReformSpec(call.receiveText())) {
                is Either.Left -> return@post call.respond(ValideringResultat.ugyldig(listOf(parsed.value)))
                is Either.Right -> parsed.value
            }
            val feil = service.validate(spec)
            call.respond(if (feil.isEmpty()) ValideringResultat.Gyldig else ValideringResultat.ugyldig(feil))
        }

        post {
            val spec = call.validSpec() ?: return@post
            call.respond(HttpStatusCode.Created, OpprettetRegulering(repository.insert(spec)))
        }

        get {
            call.respond(repository.list())
        }

        route("/{id}") {
            get {
                val id = call.id() ?: return@get
                val spec = repository.find(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(spec)
            }

            put {
                val id = call.id() ?: return@put
                val spec = call.validSpec() ?: return@put
                if (repository.update(id, spec)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            delete {
                val id = call.id() ?: return@delete
                call.respond(if (repository.delete(id)) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
            }

            get("/regulering") {
                val id = call.id() ?: return@get
                val spec = repository.find(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                when (val regulering = service.regulering(id, spec)) {
                    is Either.Right -> call.respond(regulering.value)
                    is Either.Left -> call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ValideringResultat.ugyldig(regulering.value),
                    )
                }
            }
        }
    }
}
