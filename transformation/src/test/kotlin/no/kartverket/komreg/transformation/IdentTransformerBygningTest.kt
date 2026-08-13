package no.kartverket.komreg.transformation

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.core.domain.Bygningsnummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import no.kartverket.komreg.integration.spi.Ident
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import no.kartverket.komreg.core.domain.Fylkesnummer as Fylke
import no.kartverket.komreg.core.domain.Kommunenummer.Lopenummer as Kommune
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer as Gard

class IdentTransformerBygningTest {
    private val mapping = listOf(
        identOfMatrikkelenhet(2, 5, 1) to IdentTransformerImpl.Mapping.Replace(
            identOfMatrikkelenhet(2, 6, 1)
        ),
        identOfKommune(2, 5) to IdentTransformerImpl.Mapping.Split(
            listOf(
                identOfKommune(2, 7) to null,
                identOfKommune(2, 8) to null
            )
        ),
    )
    private val identTransformer = IdentTransformerImpl(*mapping.toTypedArray())
    private val idGenerator = mockk<IdGeneratorManager>()

    @BeforeEach
    fun setup() {
        coEvery { idGenerator.idFor(any(), any() as? Any?) } returns dummyId(1)
    }

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
            val result = identTransformer.transform(entity, idGenerator::idFor)
            val expectedIdent = identOfBygningUtenFylkeOgKommune(123456789)
            val expectedAssociatedIdents = setOf(identOfMatrikkelenhet(2, 6, 1))
            assertEquals(expectedIdent, result?.single()?.transformedIdent)
            assertEquals(expectedAssociatedIdents, result?.single()?.transformedAssociatedIdents)
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
            val result = identTransformer.transform(entity, idGenerator::idFor)
            val expectedIdent = identOfBygningUtenFylkeOgKommune(123456789)
            val expectedAssociatedIdents = setOf(
                identOfMatrikkelenhet(2, 6, 1),
                identOfMatrikkelenhet(2, 10, 1),
            )
            assertEquals(expectedIdent, result?.single()?.transformedIdent)
            assertEquals(expectedAssociatedIdents, result?.single()?.transformedAssociatedIdents)
        }
    }

    private fun identOfMatrikkelenhet(fylkesnummer: Int, lopenummer: Int, gardsnummer: Int) =
        runBlocking {
            Ident(
                Fylke(fylkesnummer.toLong()),
                Kommune(lopenummer.toByte()),
                Gard(gardsnummer),
            )
        }

    private fun identOfBygning(fylkesnummer: Long, lopenummer: Int, bygningsnummer: Long) =
        runBlocking {
            Ident(
                Fylke(fylkesnummer),
                Kommune(lopenummer.toByte()),
                Bygningsnummer(bygningsnummer),
            )
        }

    private fun identOfBygningUtenFylkeOgKommune(bygningsnummer: Long) =
        runBlocking {
            Ident(Bygningsnummer(bygningsnummer))
        }

    private fun identOfKommune(fylkesnummer: Int, lopenummer: Int) = runBlocking {
        Ident(Fylke(fylkesnummer.toLong()), Kommune(lopenummer.toByte()))
    }
}
