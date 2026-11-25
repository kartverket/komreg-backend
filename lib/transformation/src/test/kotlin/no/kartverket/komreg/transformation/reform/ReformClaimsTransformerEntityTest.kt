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
 * - `Kommuneendring` 2/5 -> 3/6 er `merge` med én kilde og `into.new` (A5).
 * - `Kommuneendring` 5/5 -> 5/7 og 5/8 er `split`, der første del er etterfølger (A6).
 * - `Vegendring` 2/5/2500 -> 3/6/2500 og 3/7/2500 er et `split` av vegen med `role: identity` (A6). Det ligger
 *   under kilden 0205 i `merge.from`, siden vegen er en etterkommer av kommunen som slås sammen.
 *
 * Kommunedata for nye kommuner er [forventetKommunedata], som tilsvarer `Kommune.tilKommunedata` (A3).
 */
class ReformClaimsTransformerEntityTest {
    private val identTransformer = transformerFromSpec(
        """{ "op": "merge", "kind": "municipality",
             "from": [ { "key": ["02", "05"], "then": [
               { "op": "split", "kind": "addressCode", "key": 2500,
                 "into": [ { "new": ["03", "06", 2500], "role": "identity" }, { "new": ["03", "07", 2500] } ] } ] } ],
             "into": { "new": ["03", "06"], "payload": ${kommunePayload("Dummy")} } }""",
        """{ "op": "split", "kind": "municipality", "key": ["05", "05"],
             "into": [ { "new": ["05", "07"], "role": "successor", "payload": ${kommunePayload("Dummy")} },
                       { "new": ["05", "08"], "payload": ${kommunePayload("Dummy")} } ] }""",
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
