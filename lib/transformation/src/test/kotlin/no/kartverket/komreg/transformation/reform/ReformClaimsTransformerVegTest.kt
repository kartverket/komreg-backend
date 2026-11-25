package no.kartverket.komreg.transformation.reform

import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.transformation.dummyId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Reform-ekvivalent til `LegacyIdentTransformerVegTest`. Se avvikene A1, A6 og A9 i
 * `ReformClaimsTransformerTestSupport.kt`.
 *
 * Kommune 0205 deles i 0306 (etterfølger) og 0307. Vegene under har egne regler i `then`:
 * - veg 2500 flyttes til 0306 (legacy: én til én)
 * - veg 2600 deles i 0306 og 0307. Den flyttes (`move`) til 0306, så den beholder ID-en (legacy: første mål
 *   beholder ID-en). Delen i 0307 opprettes med `create` og `derivedFrom` under `create` for kommune 0307.
 */
class ReformClaimsTransformerVegTest {
    private val identTransformer = transformerFromSpec(
        """{ "op": "split", "kind": "municipality", "key": ["02", "05"], "successor": ["03", "06"],
             "then": [
               { "op": "move", "kind": "addressCode", "key": 2500, "to": ["03", "06", 2500] },
               { "op": "move", "kind": "addressCode", "key": 2600, "to": ["03", "06", 2600] } ] }""",
        """{ "op": "create", "kind": "municipality", "key": ["03", "06"], "derivedFrom": ["02", "05"],
             "payload": ${kommunePayload("Ny kommune 1")} }""",
        """{ "op": "create", "kind": "municipality", "key": ["03", "07"], "derivedFrom": ["02", "05"],
             "payload": ${kommunePayload("Ny kommune 2")},
             "then": [ { "op": "create", "kind": "addressCode", "key": 2600, "derivedFrom": ["02", "05", 2600] } ] }""",
    )

    @Test
    fun `Transformasjon av identer skal endres dersom en enkel ident matcher`() {
        val entity = Entity(
            dummyId(123),
            identOfVeg(2, 5, 2500),
        )
        runBlocking {
            val result = identTransformer.transform(entity, dummyIdFor)
            val expected1 = identOfVeg(3, 6, 2500)
            assertEquals(1, result.size)
            assertEquals(expected1, result[0].transformedIdent)
        }
    }

    @Test
    fun `Transformasjon av identer skal endres dersom flere identer matcher`() {
        val entity = Entity(
            dummyId(123),
            identOfVeg(2, 5, 2600),
        )
        runBlocking {
            val result = identTransformer.transform(entity, dummyIdFor)
            val expected1 = identOfVeg(3, 6, 2600)
            val expected2 = identOfVeg(3, 7, 2600)
            assertEquals(2, result.size)
            assertEquals(expected1, result[0].transformedIdent)
            assertEquals(expected2, result[1].transformedIdent)
        }
    }
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
