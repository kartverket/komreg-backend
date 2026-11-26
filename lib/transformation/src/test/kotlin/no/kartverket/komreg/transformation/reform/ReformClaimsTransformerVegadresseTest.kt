package no.kartverket.komreg.transformation.reform

import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.transformation.dummyId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Reform-ekvivalent til `LegacyIdentTransformerVegadresseTest`. Se avvik A1 i
 * `ReformClaimsTransformerTestSupport.kt`.
 *
 * Vegadresse 2/5/2600/10 flyttes til 3/6/2600/10. Kommune 0205 står i ro (`adjust`), så vegadressen skrives
 * relativt til den med pivoten `[2600, 10]`.
 */
class ReformClaimsTransformerVegadresseTest {
    private val identTransformer = transformerFromSpec(
        """{ "op": "adjust", "kind": "municipality", "key": ["02", "05"], "then": [
             { "op": "move", "kind": "addressNumber", "key": [2600, 10], "to": ["03", "06", 2600, 10] } ] }""",
    )

    @Test
    fun `A transformation of idents should change when a single ident matches`() {
        val entity = Entity(
            dummyId(123),
            identOfVegadresse(2, 5, 2600, 10),
        )
        runBlocking {
            val result = identTransformer.transform(entity, dummyIdFor)
            val expected1 = identOfVegadresse(3, 6, 2600, 10)
            assertEquals(1, result.size)
            assertEquals(expected1, result[0].transformedIdent)
        }
    }
}

private fun identOfVegadresse(
    fylkesnummer: Int,
    lopenummer: Int,
    adressekode: Int,
    adressenummer: Short,
) =
    runBlocking {
        Ident(
            Fylkesnummer(fylkesnummer.toLong()),
            Kommunenummer.Lopenummer(lopenummer.toByte()),
            Adressekode(adressekode),
            Adressenummernummer(adressenummer),
        )
    }
