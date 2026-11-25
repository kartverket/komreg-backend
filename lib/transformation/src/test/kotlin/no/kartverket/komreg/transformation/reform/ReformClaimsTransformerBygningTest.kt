package no.kartverket.komreg.transformation.reform

import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.core.domain.Bygningsnummer
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.transformation.dummyId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Reform-ekvivalent til `LegacyIdentTransformerBygningTest`. Se avvikene A1, A2, A6, A8, A9 og A10 i
 * `ReformClaimsTransformerTestSupport.kt`.
 *
 * Kommune 0205 deles i 0207 (etterfølger) og 0208, og gårdsnummer 1 i 0205 flyttes til 0206 (A10: legacy
 * `Mapping.Replace` blir `move` av `landNumber`). Legacy delte kommunen uten etterfølger, men det avviser
 * domenereglene for kommuner (A2). Bygningen får delvis nøkkel uansett, fordi kommunen utgår (A8).
 */
class ReformClaimsTransformerBygningTest {
    private val identTransformer = transformerFromSpec(
        """{ "op": "split", "kind": "municipality", "key": ["02", "05"], "successor": ["02", "07"],
             "then": [ { "op": "move", "kind": "landNumber", "key": 1, "to": ["02", "06", 1] } ] }""",
        """{ "op": "create", "kind": "municipality", "key": ["02", "07"], "derivedFrom": ["02", "05"],
             "payload": ${kommunePayload("Ny kommune 1")} }""",
        """{ "op": "create", "kind": "municipality", "key": ["02", "08"], "derivedFrom": ["02", "05"],
             "payload": ${kommunePayload("Ny kommune 2")} }""",
    )

    @Test
    fun `Transformasjon av bygning skal fjerne kommune- og fylkesdata for identen`() {
        val entity = Entity(
            id = dummyId(123),
            ident = identOfBygning(2, 5, 123456789),
            associatedIdents = setOf(
                identOfMatrikkelenhet(2, 5, 1),
            ),
        )

        runBlocking {
            val result = identTransformer.transform(entity, dummyIdFor)
            val expectedIdent = identOfBygningUtenFylkeOgKommune(123456789)
            val expectedAssociatedIdents = setOf(identOfMatrikkelenhet(2, 6, 1))
            assertEquals(expectedIdent, result.single().transformedIdent)
            assertEquals(expectedAssociatedIdents, result.single().transformedAssociatedIdents)
        }
    }

    @Test
    fun `Transformasjon skal beholde identer uten endringer`() {
        val entity = Entity(
            id = dummyId(123),
            ident = identOfBygning(2, 5, 123456789),
            associatedIdents = setOf(
                identOfMatrikkelenhet(2, 5, 1),
                identOfMatrikkelenhet(2, 10, 1),
            ),
        )

        runBlocking {
            val result = identTransformer.transform(entity, dummyIdFor)
            val expectedIdent = identOfBygningUtenFylkeOgKommune(123456789)
            val expectedAssociatedIdents = setOf(
                identOfMatrikkelenhet(2, 6, 1),
                identOfMatrikkelenhet(2, 10, 1),
            )
            assertEquals(expectedIdent, result.single().transformedIdent)
            assertEquals(expectedAssociatedIdents, result.single().transformedAssociatedIdents)
        }
    }

    private fun identOfMatrikkelenhet(fylkesnummer: Int, lopenummer: Int, gardsnummer: Int) =
        runBlocking {
            Ident(
                Fylkesnummer(fylkesnummer.toLong()),
                Kommunenummer.Lopenummer(lopenummer.toByte()),
                Matrikkelnummer.Gardsnummer(gardsnummer),
            )
        }

    private fun identOfBygning(fylkesnummer: Long, lopenummer: Int, bygningsnummer: Long) =
        runBlocking {
            Ident(
                Fylkesnummer(fylkesnummer),
                Kommunenummer.Lopenummer(lopenummer.toByte()),
                Bygningsnummer(bygningsnummer),
            )
        }

    private fun identOfBygningUtenFylkeOgKommune(bygningsnummer: Long) =
        runBlocking {
            Ident(Bygningsnummer(bygningsnummer))
        }
}
