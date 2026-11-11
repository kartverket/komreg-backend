package no.kartverket.komreg.regulering.reform.format

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test

class ReformSpecTest {
    @Test
    fun `valid fixtures decode and encode to the same JSON`() {
        ReformSpecFixtures.valid.forEach { name ->
            val raw = ReformSpecFixtures.valid(name)
            val spec = Json.decodeFromString(ReformSpec.serializer(), raw)
            val encoded = Json.encodeToJsonElement(ReformSpec.serializer(), spec)

            assertThat(encoded, name).isEqualTo(Json.parseToJsonElement(raw))
            assertThat(Json.decodeFromJsonElement(ReformSpec.serializer(), encoded), name).isEqualTo(spec)
        }
    }

    @Test
    fun `invalid shapes are rejected when decoding`() {
        ReformSpecFixtures.invalidShape.forEach { name ->
            assertFailure { Json.decodeFromString(ReformSpec.serializer(), ReformSpecFixtures.invalidShape(name)) }
                .isInstanceOf<SerializationException>()
        }
    }

    @Test
    fun `invalid segment values are not checked by the spec`() {
        ReformSpecFixtures.invalidValues.keys.forEach { name ->
            Json.decodeFromString(ReformSpec.serializer(), ReformSpecFixtures.invalidValues(name))
        }
    }

    @Test
    fun `short and long forms are kept apart`() {
        val spec = Json.decodeFromString(ReformSpec.serializer(), ReformSpecFixtures.valid("design-example"))
        val merge = spec.operasjoner[0] as OpSpec.Merge

        assertThat(merge.from[0]).isEqualTo(SourceSpec.Bare(PathSpec.Single(JsonPrimitive("03"))))
        assertThat(merge.from[1].key).isEqualTo(PathSpec.Single(JsonPrimitive("30")))
        assertThat((merge.into as IntoSpec.New).new).isEqualTo(KeySpec(listOf(JsonPrimitive("33"))))

        val adjust = spec.operasjoner[1] as OpSpec.Adjust
        assertThat(adjust.key).isEqualTo(PathSpec.Path(listOf(JsonPrimitive("46"), JsonPrimitive("01"))))
    }

    @Test
    fun `a source object without then stays an object`() {
        val json = """{"op":"merge","kind":"county","from":[{"key":"30"},["31"]],"into":{"existing":["33"]}}"""
        val op = Json.decodeFromString(OpSpec.serializer(), json)

        assertThat((op as OpSpec.Merge).from).isEqualTo(
            listOf(SourceSpec.Full(PathSpec.Single(JsonPrimitive("30"))), SourceSpec.Bare(PathSpec.Path(listOf(JsonPrimitive("31"))))),
        )
        assertThat(Json.encodeToJsonElement(OpSpec.serializer(), op)).isEqualTo(Json.parseToJsonElement(json))
    }

    @Test
    fun `format defaults to 1 and is always written`() {
        val spec = Json.decodeFromString(ReformSpec.serializer(), """{"navn":"R","ikrafttredelse":"2027-01-01","operasjoner":[]}""")

        assertThat(spec.format).isEqualTo(1)
        assertThat(Json.encodeToJsonElement(ReformSpec.serializer(), spec).jsonObject["format"]).isEqualTo(JsonPrimitive(1))
    }
}
