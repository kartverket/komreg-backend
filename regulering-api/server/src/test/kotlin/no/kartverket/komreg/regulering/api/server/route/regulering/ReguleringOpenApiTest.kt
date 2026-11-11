package no.kartverket.komreg.regulering.api.server.route.regulering

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.containsExactly
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.integration.spi.KommuneService
import no.kartverket.komreg.regulering.api.server.openApiDocs
import no.kartverket.komreg.regulering.api.server.persistence.ReformSpecRepository
import no.kartverket.komreg.regulering.api.server.service.reform.ReformSpecService
import no.kartverket.komreg.regulering.reform.domain.type.NumberingScheme
import no.kartverket.komreg.regulering.reform.format.ReformSpec
import no.kartverket.komreg.regulering.reform.format.SegmentRegistry
import no.kartverket.komreg.regulering.reform.format.v1
import kotlin.test.Test

class ReguleringOpenApiTest {
    private val repository = object : ReformSpecRepository {
        override suspend fun insert(spec: ReformSpec) = error("ikke brukt")
        override suspend fun update(id: Int, spec: ReformSpec) = error("ikke brukt")
        override suspend fun find(id: Int) = error("ikke brukt")
        override suspend fun list() = error("ikke brukt")
        override suspend fun delete(id: Int) = error("ikke brukt")
    }

    private val service = ReformSpecService(
        runBlocking { SegmentRegistry.v1(NumberingScheme.Production) },
        object : KommuneService {
            override fun findAlleFylker() = emptyList<Fylke>()
            override fun findAlleKommuner() = emptyList<Kommune>()
        },
    )

    private fun JsonObject.obj(key: String) = getValue(key).jsonObject

    private fun collectRefs(element: JsonElement): List<String> = when (element) {
        is JsonObject -> element.flatMap { (key, value) ->
            if (key == "\$ref" && value is JsonPrimitive) listOf(value.content) else collectRefs(value)
        }
        is JsonArray -> element.flatMap(::collectRefs)
        else -> emptyList()
    }

    @Test
    fun `openapi-dokumentet beskriver alle endepunktene og skjemaene`() = testApplication {
        application { routing { reguleringer(Json, repository, service); openApiDocs() } }
        val response = client.get("/openapi/openapi.json")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val doc = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        val expected = mapOf(
            "/api/reguleringer/validering" to mapOf("post" to listOf("200")),
            "/api/reguleringer" to mapOf("post" to listOf("201", "422"), "get" to listOf("200")),
            "/api/reguleringer/{id}" to mapOf(
                "get" to listOf("200", "400", "404"),
                "put" to listOf("204", "400", "404", "422"),
                "delete" to listOf("204", "400", "404"),
            ),
            "/api/reguleringer/{id}/regulering" to mapOf("get" to listOf("200", "400", "404", "422")),
        )
        val paths = doc.obj("paths")
        expected.forEach { (path, methods) ->
            methods.forEach { (method, statuses) ->
                val operation = paths.obj(path).obj(method)
                assertThat(operation.getValue("summary").jsonPrimitive.content).isNotEmpty()
                assertThat(operation.obj("responses").keys).containsAtLeast(*statuses.toTypedArray())
                assertThat(operation.getValue("tags")).isEqualTo(JsonArray(listOf(JsonPrimitive("Reguleringer"))))
                if (path.contains("{id}")) {
                    assertThat(operation.getValue("parameters").jsonArray.map { it.jsonObject["name"] })
                        .containsExactly(JsonPrimitive("id"))
                }
                if (method != "get" && method != "delete") {
                    assertThat(operation["requestBody"]).isNotNull()
                }
            }
        }

        val schemas = doc.obj("components").obj("schemas")
        assertThat(schemas.keys).containsAtLeast(
            "ReformSpec", "OpSpec", "AdjustOp", "MoveOp", "MergeOp", "SplitOp", "CreateOp", "SourceOpSpec", "PathSpec",
            "KeySpec", "Kind", "KommunePayload", "FylkePayload", "ValideringResultat", "ReformFeil",
            "OpprettetRegulering", "ReformSammendrag", "Regulering", "TransformasjonDTO",
        )

        val unresolved = collectRefs(doc).filter { it.removePrefix("#/components/schemas/") !in schemas.keys }
        assertThat(unresolved).isEmpty()
    }
}
