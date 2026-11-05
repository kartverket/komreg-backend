package no.kartverket.komreg.regulering.api.server.route.regulering

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sse.sse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.komreg.regulering.api.server.persistence.ReformRepository

@Serializable
data class MatrikkelenhetDraft(
    val kommunenummer: String,
    val gardsnummer: Int,
    // null betyr hele gårdsnummeret flyttes
    val bruksnummer: Int? = null,
    val teigIder: Set<Long>,
    // Ikke satt (null) betyr dagens nummer beholdes
    val nyGardsnummer: Int? = null,
    val nyBruksnummer: Int? = null,
)

@Serializable
data class GrensejusteringDraft(
    val fraKommunenummer: String,
    val tilKommunenummer: String,
    val matrikkelenheter: Set<MatrikkelenhetDraft>,
)

@Serializable
data class ReguleringDetail(
    val id: Int,
    val navn: String,
    val dato: LocalDate,
    val grensejustering: GrensejusteringDraft,
)

@Serializable
data class OpprettReguleringRequest(
    val navn: String,
    val dato: LocalDate,
    val grensejustering: GrensejusteringDraft,
)


fun Route.reguleringer(appJson: Json, reforms: ReformRepository) {
    val changes = MutableSharedFlow<ReguleringDetail>(extraBufferCapacity = 64)

    route("/api/reguleringer") {
        post {
            val request = call.receive<OpprettReguleringRequest>()
            val regulering = try {
                reforms.insert(request)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Ugyldig regulering")
                return@post
            }
            changes.emit(regulering)
            call.respond(HttpStatusCode.Created, regulering)
        }

        sse("/stream") {
            changes
                .onSubscription {
                    send(data = appJson.encodeToString(reforms.all().toList()), event = "snapshot")
                }
                .collect { send(data = Json.encodeToString(it), event = "upsert") }
        }
    }
}
