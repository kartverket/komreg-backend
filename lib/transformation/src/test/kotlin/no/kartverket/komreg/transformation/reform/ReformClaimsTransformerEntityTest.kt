package no.kartverket.komreg.transformation.reform

import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.transformation.dummyId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Reform-ekvivalent til `LegacyTransformEntityTest`. Se avvikene A1, A3, A5 og A6 i
 * `ReformClaimsTransformerTestSupport.kt`. `LegacyTransformEntityTest` brukte `Reguleringsinput` og `mapInput`.
 * Her skrives de tilsvarende endringene som spec:
 *
 * - `Kommuneendring` 2/5 -> 3/6 er `merge` med én kilde, og `create` med `derivedFrom` for 0306 (A5).
 * - `Kommuneendring` 5/5 -> 5/7 og 5/8 er `split` med første del som `successor`, og `create` med
 *   `derivedFrom` for hver del (A6).
 * - `Vegendring` 2/5/2500 -> 3/6/2500 og 3/7/2500 er en `move` av vegen til 3/6/2500, og `create` med
 *   `derivedFrom` for 3/7/2500 (A6). `move` ligger under kilden 0205 i `merge.from`, siden vegen er en
 *   etterkommer av kommunen som slås sammen. `create` ligger på rotnivå, med full nøkkel.
 *
 * Kommunedata for nye kommuner er [forventetKommunedata], som tilsvarer `Kommune.tilKommunedata` (A3).
 */
class ReformClaimsTransformerEntityTest {
    private val identTransformer = transformerFromSpec(
        """{ "op": "merge", "kind": "municipality",
             "from": [ { "key": ["02", "05"], "then": [
               { "op": "move", "kind": "addressCode", "key": 2500, "to": ["03", "06", 2500] } ] } ],
             "into": ["03", "06"] }""",
        """{ "op": "create", "kind": "municipality", "key": ["03", "06"], "derivedFrom": ["02", "05"],
             "payload": ${kommunePayload("Dummy")} }""",
        """{ "op": "create", "kind": "addressCode", "key": ["03", "07", 2500], "derivedFrom": ["02", "05", 2500] }""",
        """{ "op": "split", "kind": "municipality", "key": ["05", "05"], "successor": ["05", "07"] }""",
        """{ "op": "create", "kind": "municipality", "key": ["05", "07"], "derivedFrom": ["05", "05"],
             "payload": ${kommunePayload("Dummy")} }""",
        """{ "op": "create", "kind": "municipality", "key": ["05", "08"], "derivedFrom": ["05", "05"],
             "payload": ${kommunePayload("Dummy")} }""",
    )

    @Test
    fun `Flytting av kommune skal returnere to transformasjoner, en for utgått og en for opprettelse av ny kommune`() {
        val entity =
            Entity(
                dummyId(123),
                identOfKommune(2, 5),
            )
        runBlocking {
            val result = identTransformer.transform(entity, dummyIdFor)
            val expected = identOfKommune(3, 6)

            assertEquals(2, result.size)
            assertEquals(null, result[0].resultObject)
            assertEquals(expected, result[0].transformedIdent)
            assertEquals(forventetKommunedata("Dummy"), result[1].resultObject)
            assertEquals(expected, result[1].transformedIdent)
        }
    }

    @Test
    fun `Splitting av kommune skal returnere tre transformasjoner`() {
        val entity =
            Entity(
                dummyId(123),
                identOfKommune(5, 5),
            )
        runBlocking {
            val result = identTransformer.transform(entity, dummyIdFor)

            val expected = identOfKommune(5, 7)
            val expected2 = identOfKommune(5, 8)

            assertEquals(3, result.size)
            assertEquals(null, result[0].resultObject)
            assertEquals(expected, result[0].transformedIdent)
            assertEquals(expected, result[1].transformedIdent)
            assertEquals(forventetKommunedata("Dummy"), result[1].resultObject)
            assertEquals(expected2, result[2].transformedIdent)
            assertEquals(forventetKommunedata("Dummy"), result[2].resultObject)
        }
    }

    @Test
    fun `Veg under kommune som slås sammen skal deles etter egen regel, ikke følge kommunen`() {
        val entity =
            Entity(
                dummyId(123),
                identOfVeg(2, 5, 2500),
            )
        runBlocking {
            val result = identTransformer.transform(entity, dummyIdFor)

            assertEquals(listOf(identOfVeg(3, 6, 2500), identOfVeg(3, 7, 2500)), result.map { it.transformedIdent })
        }
    }

    @Test
    fun `Transformasjon av identer skal ikke transformere entiteten når ingen av dens identer matcher`() {
        val entity =
            Entity(
                dummyId(123),
                identOfKommune(10, 50),
            )
        runBlocking {
            val result = identTransformer.transform(entity, dummyIdFor)
            assertEquals(emptyList<Transformation>(), result)
        }
    }

    @Test
    fun `Transformasjon av assosierte identer skal endre alle identer den finner en match på`() {
        val entity =
            Entity(
                dummyId(123),
                associatedIdents =
                setOf(
                    identOfMatrikkelenhet(2, 5, 1),
                    identOfMatrikkelenhet(2, 5, 2),
                ),
            )
        runBlocking {
            val result = identTransformer.transform(entity, dummyIdFor)
            val expected =
                setOf(
                    identOfMatrikkelenhet(3, 6, 1),
                    identOfMatrikkelenhet(3, 6, 2),
                )
            assertEquals(expected, result.single().transformedAssociatedIdents)
        }
    }

    @Test
    fun `Transformasjon av assosierte identer skal kun endre de identene den får match på, inneholder ident uten match`() {
        val entity =
            Entity(
                dummyId(123),
                associatedIdents =
                setOf(
                    identOfMatrikkelenhet(2, 5, 1),
                    identOfMatrikkelenhet(10, 15, 1),
                ),
            )
        runBlocking {
            val result = identTransformer.transform(entity, dummyIdFor)
            val expected =
                setOf(
                    identOfMatrikkelenhet(3, 6, 1),
                    identOfMatrikkelenhet(10, 15, 1),
                )
            assertEquals(expected, result.single().transformedAssociatedIdents)
        }
    }

    @Test
    fun `Transformasjon av assosierte identer skal kun endre de identene den får match på, motsatt rekkefølge på ident uten match`() {
        val entity =
            Entity(
                dummyId(123),
                associatedIdents =
                setOf(
                    identOfMatrikkelenhet(10, 15, 1),
                    identOfMatrikkelenhet(2, 5, 1),
                ),
            )
        runBlocking {
            val result = identTransformer.transform(entity, dummyIdFor)
            val expected =
                setOf(
                    identOfMatrikkelenhet(10, 15, 1),
                    identOfMatrikkelenhet(3, 6, 1),
                )
            assertEquals(expected, result.single().transformedAssociatedIdents)
        }
    }
}

private fun identOfMatrikkelenhet(
    fylkesnummer: Int,
    lopenummer: Int,
    gardsnummer: Int,
) = runBlocking {
    Ident(
        Fylkesnummer(fylkesnummer.toLong()),
        Kommunenummer.Lopenummer(lopenummer.toByte()),
        Matrikkelnummer.Gardsnummer(gardsnummer),
    )
}

private fun identOfKommune(
    fylkesnummer: Int,
    lopenummer: Int,
) = runBlocking {
    Ident(Fylkesnummer(fylkesnummer.toLong()), Kommunenummer.Lopenummer(lopenummer.toByte()))
}

private fun identOfVeg(
    fylkesnummer: Int,
    lopenummer: Int,
    adressekode: Int,
) = runBlocking {
    Ident(
        Fylkesnummer(fylkesnummer.toLong()),
        Kommunenummer.Lopenummer(lopenummer.toByte()),
        Adressekode(adressekode),
    )
}
