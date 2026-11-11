package no.kartverket.komreg.regulering.api.server.route.regulering

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Koordinat
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.integration.spi.KommuneService
import no.kartverket.komreg.regulering.api.server.persistence.ReformSammendrag
import no.kartverket.komreg.regulering.api.server.persistence.ReformSpecRepository
import no.kartverket.komreg.regulering.api.server.service.reform.ReformSpecService
import no.kartverket.komreg.regulering.reform.domain.type.NumberingScheme
import no.kartverket.komreg.regulering.reform.format.ReformSpec
import no.kartverket.komreg.regulering.reform.format.ReformSpecFixtures
import no.kartverket.komreg.regulering.reform.format.SegmentRegistry
import no.kartverket.komreg.regulering.reform.format.v1
import kotlin.test.Test

class ReguleringRouteTest {
    private val json = Json

    private class InMemoryRepository : ReformSpecRepository {
        val specs = linkedMapOf<Int, ReformSpec>()
        private var nextId = 1

        override suspend fun insert(spec: ReformSpec): Int = nextId++.also { specs[it] = spec }

        override suspend fun update(id: Int, spec: ReformSpec): Boolean =
            (id in specs).also { if (it) specs[id] = spec }

        override suspend fun find(id: Int): ReformSpec? = specs[id]

        override suspend fun list() = specs.map { (id, spec) -> ReformSammendrag(id, spec.navn, spec.ikrafttredelse) }

        override suspend fun delete(id: Int): Boolean = specs.remove(id) != null
    }

    private fun kommune(nummer: Long) = Kommune(
        nummer, "K$nummer", null, Koordinatsystem.UTM32, Koordinat(0.0, 0.0), false, "", null, null, ByteArray(0),
    )

    private val kommuneService = object : KommuneService {
        override fun findAlleFylker() = listOf(Fylke(46, "Vestland", null))
        override fun findAlleKommuner() = listOf(kommune(4601), kommune(4602), kommune(4603))
    }

    private val repository = InMemoryRepository()
    private val service = ReformSpecService(
        runBlocking { SegmentRegistry.v1(NumberingScheme.Production) },
        kommuneService,
    )

    private fun test(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json(json) }
            routing { reguleringer(json, repository, service) }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String) =
        client.post(path) { contentType(ContentType.Application.Json); setBody(body) }

    private suspend fun ApplicationTestBuilder.put(path: String, body: String) =
        client.put(path) { contentType(ContentType.Application.Json); setBody(body) }

    private suspend fun HttpResponse.jsonBody(): JsonElement = json.parseToJsonElement(bodyAsText())

    private suspend fun HttpResponse.errors(): List<JsonObject> =
        jsonBody().jsonObject.getValue("feil").jsonArray.map { it.jsonObject }

    private fun JsonObject.field(name: String) = getValue(name).jsonPrimitive.content

    private val valid = ReformSpecFixtures.valid("move")

    // Passerer formatet, men er ikke gyldig i domenet (kommuner kan ikke flyttes) og peker på en ukjent kommune
    private val invalidInTwoSteps = """
        { "format": 1, "navn": "Feil", "ikrafttredelse": "2027-01-01", "operasjoner": [
            { "op": "adjust", "kind": "county", "key": "46", "then": [
                { "op": "move", "kind": "municipality", "key": "99", "to": ["46", "98"] }
            ] }
        ] }
    """.trimIndent()

    @Test
    fun `gyldig spec lagres og kan hentes tilbake`() = test {
        val created = post("/api/reguleringer", valid)
        assertThat(created.status).isEqualTo(HttpStatusCode.Created)
        val id = created.jsonBody().jsonObject.field("id")

        val fetched = client.get("/api/reguleringer/$id")
        assertThat(fetched.status).isEqualTo(HttpStatusCode.OK)
        assertThat(fetched.jsonBody()).isEqualTo(json.parseToJsonElement(valid))
    }

    @Test
    fun `regulering avledes ved kallet med reguleringens id`() = test {
        val id = post("/api/reguleringer", valid).jsonBody().jsonObject.field("id")

        val regulering = client.get("/api/reguleringer/$id/regulering")
        assertThat(regulering.status).isEqualTo(HttpStatusCode.OK)
        val dto = regulering.jsonBody().jsonObject
        assertThat(dto.field("id")).isEqualTo(id)
        assertThat(dto.field("navn")).isEqualTo("Flytting av veg og teig")
        assertThat(dto.getValue("endringer").jsonArray).isNotEmpty()
    }

    @Test
    fun `ugyldig spec gir 422 med stier og lagres ikke`() = test {
        val response = post("/api/reguleringer", ReformSpecFixtures.invalidValues("county-one-digit"))

        assertThat(response.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
        assertThat(response.jsonBody().jsonObject.field("gyldig")).isEqualTo("false")
        val error = response.errors().single()
        assertThat(error.field("sti")).isEqualTo("$.operasjoner[0].key")
        assertThat(error.field("steg")).isEqualTo("format")
        assertThat(error.field("kode")).isEqualTo("InvalidSegment")
        assertThat(repository.specs).isEmpty()
    }

    @Test
    fun `spec med feil form gir 422 med steg form`() = test {
        val response = post("/api/reguleringer", ReformSpecFixtures.invalidShape("unknown-op"))

        assertThat(response.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
        assertThat(response.errors().map { it.field("steg") }).containsExactly("form")
    }

    @Test
    fun `validering lagrer ikke`() = test {
        val ok = post("/api/reguleringer/validering", valid)
        assertThat(ok.status).isEqualTo(HttpStatusCode.OK)
        assertThat(ok.jsonBody().jsonObject.field("gyldig")).isEqualTo("true")
        assertThat(ok.jsonBody().jsonObject.getValue("feil")).isEqualTo(JsonArray(emptyList()))

        val invalid = post("/api/reguleringer/validering", invalidInTwoSteps)
        assertThat(invalid.status).isEqualTo(HttpStatusCode.OK)
        assertThat(invalid.jsonBody().jsonObject.field("gyldig")).isEqualTo("false")

        assertThat(repository.specs).isEmpty()
    }

    @Test
    fun `spec med feil i flere steg gir bare feilene fra første steg som feiler`() = test {
        val errors = post("/api/reguleringer/validering", invalidInTwoSteps).errors()

        assertThat(errors.map { it.field("steg") }).containsExactly("domene")
        assertThat(errors.single().field("kode")).isEqualTo("ActionNotSupported")
        assertThat(errors.single().field("sti")).isEqualTo("$.operasjoner[0].then[0]")
    }

    @Test
    fun `dataregler oversettes til stier`() = test {
        val spec = """
            { "format": 1, "navn": "Ukjent", "ikrafttredelse": "2027-01-01", "operasjoner": [
                { "op": "merge", "kind": "municipality", "from": [["46", "99"]], "into": { "existing": ["46", "02"] } }
            ] }
        """.trimIndent()

        val error = post("/api/reguleringer/validering", spec).errors().single()

        assertThat(error.field("steg")).isEqualTo("data")
        assertThat(error.field("kode")).isEqualTo("SourceNotFound")
        assertThat(error.field("sti")).isEqualTo("$.operasjoner[0].from[0]")
    }

    @Test
    fun `put erstatter specen, og gir 422 og 404 ved feil`() = test {
        val id = post("/api/reguleringer", valid).jsonBody().jsonObject.field("id")
        val replacement = valid.replace("Flytting av veg og teig", "Nytt navn")

        assertThat(put("/api/reguleringer/$id", replacement).status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(client.get("/api/reguleringer/$id").jsonBody().jsonObject.field("navn")).isEqualTo("Nytt navn")

        assertThat(put("/api/reguleringer/$id", invalidInTwoSteps).status).isEqualTo(HttpStatusCode.UnprocessableEntity)
        assertThat(repository.specs.getValue(id.toInt()).navn).isEqualTo("Nytt navn")

        assertThat(put("/api/reguleringer/999", valid).status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `liste gir sammendrag, og delete fjerner reguleringen`() = test {
        val id = post("/api/reguleringer", valid).jsonBody().jsonObject.field("id")

        val summary = client.get("/api/reguleringer").jsonBody().jsonArray.single().jsonObject
        assertThat(summary.field("id")).isEqualTo(id)
        assertThat(summary.field("navn")).isEqualTo("Flytting av veg og teig")
        assertThat(LocalDate.parse(summary.field("ikrafttredelse"))).isEqualTo(LocalDate(2027, 1, 1))

        assertThat(client.delete("/api/reguleringer/$id").status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(client.get("/api/reguleringer/$id").status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(client.delete("/api/reguleringer/$id").status).isEqualTo(HttpStatusCode.NotFound)
    }
}
