package no.kartverket.komreg.transformation

import arrow.core.getOrElse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.parameter.op.*
import no.kartverket.komreg.parameter.op.SubOp.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import no.kartverket.komreg.core.domain.Bygningsnummer as Bygg
import no.kartverket.komreg.core.domain.Fylkesnummer as Fylke
import no.kartverket.komreg.core.domain.Kommunenummer.Lopenummer as Kommune

import no.kartverket.komreg.parameter.data.tuple.syntax.*

class IdentTransformerBygningTest {
    private val mapping = LoOpProgram
        .compile(
            Adjust(Fylke(2), listOf(
                Split(Kommune(5), listOf(
                    Move(Matrikkelnummer.Gardsnummer(1), +Fylke(2) / Kommune(6) / Matrikkelnummer.Gardsnummer(1)),
                ))
            )),
        ).getOrElse { err -> throw IllegalStateException("Failed to compile: ${err.joinToString("\n\t - ", "\n\t - ")}") }
    private val identTransformer = IdentTransformer(mapping)
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
                Kommunenummer.Lopenummer(lopenummer.toByte()),
                Matrikkelnummer.Gardsnummer(gardsnummer),
            )
        }

    private fun identOfBygning(fylkesnummer: Long, lopenummer: Int, bygningsnummer: Long) =
        runBlocking {
            Ident(
                Fylke(fylkesnummer),
                Kommunenummer.Lopenummer(lopenummer.toByte()),
                Bygg(bygningsnummer),
            )
        }

    private fun identOfBygningUtenFylkeOgKommune(bygningsnummer: Long) =
        runBlocking {
            Ident(Bygg(bygningsnummer))
        }

    private fun identOfKommune(fylkesnummer: Int, lopenummer: Int) = runBlocking {
        Ident(Fylke(fylkesnummer.toLong()), Kommunenummer.Lopenummer(lopenummer.toByte()))
    }
}
