package no.kartverket.komreg.regulering.reform.format

import arrow.core.Either
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Koordinat
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.core.domain.Kretsnummer
import no.kartverket.komreg.core.domain.Kretstype
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.core.domain.PostadresseForOppretting
import no.kartverket.komreg.core.domain.TeigId
import no.kartverket.komreg.integration.spi.identTypeOf1
import no.kartverket.komreg.integration.spi.identTypeOf2
import no.kartverket.komreg.integration.spi.identTypeOf3
import no.kartverket.komreg.integration.spi.identTypeOf4
import no.kartverket.komreg.integration.spi.identTypeOf7
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.regulering.reform.domain.type.NumberingScheme
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SegmentRegistryTest {
    private val registry = runBlocking { SegmentRegistry.v1(NumberingScheme.Production) }

    private fun kind(name: String) = registry[name]!!

    private fun SegmentKind<*>.decodeOrThrow(value: Any): Any =
        decode(if (value is String) JsonPrimitive(value) else JsonPrimitive(value as Number)).getOrNull()!!

    private fun key(vararg segments: Pair<String, Any>): Key.Snoc =
        segments.fold<Pair<String, Any>, Key>(Key.Empty) { key, (name, value) ->
            key.snoc(kind(name), kind(name).decodeOrThrow(value))
        } as Key.Snoc

    @Test
    fun `kind paths give the same ident types as ReformMapping`() = runBlocking {
        val expected = mapOf(
            V1Kinds.COUNTY to identTypeOf1<Fylkesnummer>(),
            V1Kinds.MUNICIPALITY to identTypeOf2<Fylkesnummer, Kommunenummer.Lopenummer>(),
            V1Kinds.ADDRESS_CODE to identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode>(),
            V1Kinds.ADDRESS_NUMBER to
                identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode, Adressenummernummer>(),
            V1Kinds.DISTRICT_TYPE to identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Kretstype>(),
            V1Kinds.DISTRICT_NUMBER to identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Kretstype, Kretsnummer>(),
            V1Kinds.LAND_NUMBER to identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Matrikkelnummer.Gardsnummer>(),
            V1Kinds.TITLE_NUMBER to identTypeOf4<
                Fylkesnummer,
                Kommunenummer.Lopenummer,
                Matrikkelnummer.Gardsnummer,
                Matrikkelnummer.Bruksnummer,
                >(),
            V1Kinds.PARCEL to identTypeOf7<
                Fylkesnummer,
                Kommunenummer.Lopenummer,
                Matrikkelnummer.Gardsnummer,
                Matrikkelnummer.Bruksnummer,
                Matrikkelnummer.Festenummer,
                Matrikkelnummer.Seksjonsnummer,
                TeigId,
                >(),
        )

        assertThat(registry.kinds.map { it.name }.toSet()).isEqualTo(expected.keys)
        expected.forEach { (name, type) ->
            assertThat(registry.identType(kind(name)), name).isSameInstanceAs(type)
            assertThat(registry.kindOf(type), name).isSameInstanceAs(kind(name))
        }
        assertThat(registry.kindOf(identTypeOf2<Fylkesnummer, Kretstype>())).isNull()
    }

    @Test
    fun `keys convert to the idents ReformMapping uses`() = runBlocking {
        val type = identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Matrikkelnummer.Gardsnummer, Matrikkelnummer.Bruksnummer>()
        val key = key(V1Kinds.COUNTY to "46", V1Kinds.MUNICIPALITY to "01", V1Kinds.LAND_NUMBER to 12, V1Kinds.TITLE_NUMBER to 3)

        assertThat(key.toIdents(registry)).containsExactly(
            type(Fylkesnummer(46), Kommunenummer.Lopenummer(1), Matrikkelnummer.Gardsnummer(12), Matrikkelnummer.Bruksnummer(3)),
        )
    }

    @Test
    fun `parcel expands to bnr 0 and bnr 1`() = runBlocking {
        val type = identTypeOf7<
            Fylkesnummer,
            Kommunenummer.Lopenummer,
            Matrikkelnummer.Gardsnummer,
            Matrikkelnummer.Bruksnummer,
            Matrikkelnummer.Festenummer,
            Matrikkelnummer.Seksjonsnummer,
            TeigId,
            >()
        val key = key(V1Kinds.COUNTY to "46", V1Kinds.MUNICIPALITY to "01", V1Kinds.PARCEL to 987654)

        assertThat(key.toIdents(registry)).containsExactly(
            *listOf<Short>(0, 1).map { bnr ->
                type(
                    Fylkesnummer(46),
                    Kommunenummer.Lopenummer(1),
                    Matrikkelnummer.Gardsnummer(0),
                    Matrikkelnummer.Bruksnummer(bnr),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                    TeigId(987654),
                )
            }.toTypedArray(),
        )
    }

    @Test
    fun `invalid segment values give errors and not exceptions`() {
        val invalid = listOf(
            V1Kinds.COUNTY to JsonPrimitive("3"),
            V1Kinds.COUNTY to JsonPrimitive("10001"),
            V1Kinds.COUNTY to JsonPrimitive(46),
            V1Kinds.MUNICIPALITY to JsonPrimitive("1"),
            V1Kinds.ADDRESS_CODE to JsonPrimitive(0),
            V1Kinds.ADDRESS_CODE to JsonPrimitive("1234"),
            V1Kinds.ADDRESS_NUMBER to JsonPrimitive(70000),
            V1Kinds.DISTRICT_TYPE to JsonPrimitive(""),
            V1Kinds.DISTRICT_NUMBER to JsonPrimitive(1.5),
            V1Kinds.LAND_NUMBER to JsonPrimitive(100000),
            V1Kinds.TITLE_NUMBER to JsonPrimitive(true),
            V1Kinds.PARCEL to JsonPrimitive("987654"),
        )
        invalid.forEach { (name, value) ->
            val result = kind(name).decode(value)
            assertThat(result, "$name $value").isInstanceOf<Either.Left<SegmentErr.InvalidValue>>()
            assertThat((result as Either.Left).value.kind).isEqualTo(name)
        }
    }

    @Test
    fun `invalid value fixtures have an invalid segment at the given path`() {
        ReformSpecFixtures.invalidValues.forEach { (name, segment) ->
            val value = Json.parseToJsonElement(ReformSpecFixtures.invalidValues(name)).at(segment.path) as JsonPrimitive
            assertThat(kind(segment.kind).decode(value).isLeft(), name).isTrue()
        }
    }

    @Test
    fun `test scheme accepts five digit counties`() = runBlocking {
        val test = SegmentRegistry.v1(NumberingScheme.Test)

        assertThat(test[V1Kinds.COUNTY]!!.decode(JsonPrimitive("10001")).isRight()).isTrue()
        assertThat(test[V1Kinds.COUNTY]!!.decode(JsonPrimitive("100")).isLeft()).isTrue()
    }

    @Test
    fun `payloads map to core types with ikrafttredelse from the spec`() {
        val dato = LocalDate(2027, 1, 1)
        val kommune = Json.parseToJsonElement(
            """
            { "navn": "Nye Kommune", "koordinatsystem": "UTM32", "senterpunkt": { "x": 1.0, "y": 2.0 },
              "godkjenteGardsnumre": [ { "fra": 1, "til": 200 }, { "fra": 300, "til": 300 } ],
              "adresse": { "adresselinje1": " Gate 1 ", "adresselinje2": " ", "postnummer": "0101" },
              "kommunevapen": "AQID" }
            """,
        ).jsonObject

        assertThat(kind(V1Kinds.MUNICIPALITY).decodePayload(kommune, dato).getOrNull()).isEqualTo(
            Kommunedata(
                navn = "NYE KOMMUNE",
                koordinatsystem = Koordinatsystem.UTM32,
                senterpunkt = Koordinat(1.0, 2.0),
                nedsattKonsesjonsgrense = false,
                godkjenteGardsnumre = "1-200,300",
                adresse = PostadresseForOppretting("Gate 1", null, "0101"),
                standardRekvirentOrgnummer = null,
                ikrafttredelsesdato = dato,
                kommunevapen = byteArrayOf(1, 2, 3),
            ),
        )
        assertThat(kind(V1Kinds.COUNTY).decodePayload(json("""{"navn":"Buskerud"}"""), dato).getOrNull())
            .isEqualTo(Fylkesdata("Buskerud"))
    }

    @Test
    fun `invalid payloads give errors`() {
        val dato = LocalDate(2027, 1, 1)
        val base = """"navn":"K","koordinatsystem":"UTM32","senterpunkt":{"x":1.0,"y":2.0}"""
        val invalid = listOf(
            V1Kinds.MUNICIPALITY to """{"navn":"K"}""",
            V1Kinds.MUNICIPALITY to """{$base,"kommunevapen":"ikke base64!"}""",
            V1Kinds.MUNICIPALITY to """{$base,"godkjenteGardsnumre":[{"fra":5,"til":1}]}""",
            V1Kinds.MUNICIPALITY to """{$base,"koordinatsystem":"UTM99"}""",
            V1Kinds.COUNTY to """{"navn":"F","ukjent":1}""",
        )
        invalid.forEach { (name, payload) ->
            assertThat(kind(name).decodePayload(json(payload), dato), payload)
                .isInstanceOf<Either.Left<SegmentErr.InvalidPayload>>()
        }
        assertThat(kind(V1Kinds.ADDRESS_CODE).decodePayload(json("{}"), dato))
            .isEqualTo(Either.Left(SegmentErr.PayloadNotAllowed(V1Kinds.ADDRESS_CODE)))
    }

    @Test
    fun `keys follow the parent chain`() {
        val key = key(V1Kinds.COUNTY to "46", V1Kinds.MUNICIPALITY to "01", V1Kinds.ADDRESS_CODE to 1234)

        assertThat(key.ancestorsAndSelf().map { it.kind.name }.toList())
            .containsExactly(V1Kinds.ADDRESS_CODE, V1Kinds.MUNICIPALITY, V1Kinds.COUNTY)
        assertThat(kind(V1Kinds.ADDRESS_NUMBER).path.map { it.name })
            .containsExactly(V1Kinds.COUNTY, V1Kinds.MUNICIPALITY, V1Kinds.ADDRESS_CODE, V1Kinds.ADDRESS_NUMBER)
        assertFailsWith<IllegalArgumentException> {
            Key.Empty.snoc(kind(V1Kinds.MUNICIPALITY), kind(V1Kinds.MUNICIPALITY).decodeOrThrow("01"))
        }
    }

    private fun json(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    private fun JsonElement.at(path: String): JsonElement =
        Regex("""\.(\w+)|\[(\d+)]""").findAll(path.removePrefix("$")).fold(this) { element, match ->
            val (field, index) = match.destructured
            if (field.isNotEmpty()) (element as JsonObject).getValue(field) else (element as JsonArray)[index.toInt()]
        }
}
