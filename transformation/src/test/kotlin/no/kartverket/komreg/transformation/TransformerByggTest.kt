package no.kartverket.komreg.transformation

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.builtins.serializer
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import no.kartverket.komreg.integration.spi.Ident
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class TransformerByggTest {
    private val reguleringsInput = Reguleringsinput(
        id = "123",
        LocalDate.now().toKotlinLocalDate(),
        listOf(
            Matrikkelenhetendring(
                fylkesnummer = FraTil(
                    fra = Fylkesnummer(2),
                    til = Fylkesnummer(2),
                ),
                kommuneløpenummer = FraTil(
                    fra = Kommunenummer.Lopenummer(5),
                    til = Kommunenummer.Lopenummer(6),
                ),
                gårdsnummer = FraTil(
                    fra = Matrikkelnummer.Gardsnummer(1),
                    til = Matrikkelnummer.Gardsnummer(1),
                ),
            ),
            Matrikkelenhetendring(
                fylkesnummer = FraTil(
                    fra = Fylkesnummer(3),
                    til = Fylkesnummer(4),
                ),
                kommuneløpenummer = FraTil(
                    fra = Kommunenummer.Lopenummer(5),
                    til = Kommunenummer.Lopenummer(6),
                ),
                gårdsnummer = FraTil(
                    fra = Matrikkelnummer.Gardsnummer(1),
                    til = Matrikkelnummer.Gardsnummer(1),
                ),
            ),
        ),
        emptyList(),
        emptyList(),
    )

    private val idGenerator = mockk<IdGeneratorManager>()

    @BeforeEach
    fun setup() {
        every { idGenerator.idFor(any()) } returns dummyId(1)
    }

    @Test
    fun `A transformation of bygning should set bygning-ident empty, same fylke`() {
        val entity = Entity(
            id = dummyId(123),
            ident = identOfBygning(2, 5, 123456789),
            associatedIdents = setOf(
                identOfMatrikkelenhet(2, 5, 1),
            ),
        )

        val result = transformerEntity(reguleringsInput, entity, idGenerator)

        val expectedIdent = identOfBygningUtenFylkeOgKommune(123456789)
        val expectedAssociatedIdents = setOf(identOfMatrikkelenhet(2, 6, 1))
        assertEquals(expectedIdent, result?.single()?.transformedIdent)
        assertEquals(expectedAssociatedIdents, result?.single()?.transformedAssociatedIdents)
    }

    @Test
    fun `A transformation of bygning should set bygning-ident empty, different fylke`() {
        val entity = Entity(
            id = dummyId(123),
            ident = identOfBygning(3, 5, 123456789),
            associatedIdents = setOf(
                identOfMatrikkelenhet(3, 5, 1),
            ),
        )
        val expectedIdent = identOfBygningUtenFylkeOgKommune(123456789)
        val result = transformerEntity(reguleringsInput, entity, idGenerator)
        val expectedAssociatedIdents = setOf(identOfMatrikkelenhet(4, 6, 1))
        assertEquals(expectedIdent, result?.single()?.transformedIdent)
        assertEquals(expectedAssociatedIdents, result?.single()?.transformedAssociatedIdents)
    }

    @Test
    fun `A transformation of bygning should set bygning-ident empty, associated ident`() {
        val entity = Entity(
            id = dummyId(123),
            associatedIdents = setOf(
                identOfMatrikkelenhet(3, 5, 1),
                identOfBygning(3, 5, 123456789),
            ),
        )

        val result = transformerEntity(reguleringsInput, entity, idGenerator)

        val expectedAssociatedIdents = setOf(
            identOfMatrikkelenhet(4, 6, 1),
            identOfBygningUtenFylkeOgKommune(123456789),
        )
        assertEquals(expectedAssociatedIdents, result?.single()?.transformedAssociatedIdents)
    }

    @Test
    fun `A transformation of sefrakminne should set ident empty`() {
        val entity = Entity(
            id = sefrakId(123),
            ident = identOfSefrakUnknown(3, 5, "X"),
            associatedIdents = setOf(
                identOfMatrikkelenhet(3, 5, 1),
            ),
        )

        val result = transformerEntity(reguleringsInput, entity, idGenerator)

        val expectedIdent = Ident.Empty
        val expectedAssociatedIdents = setOf(identOfMatrikkelenhet(4, 6, 1))

        assertEquals(expectedIdent, result?.single()?.transformedIdent)
        assertEquals(expectedAssociatedIdents, result?.single()?.transformedAssociatedIdents)
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

    private fun identOfSefrakUnknown(fylkesnummer: Long, lopenummer: Int, x: String) =
        runBlocking {
            Ident(
                Fylkesnummer(fylkesnummer),
                Kommunenummer.Lopenummer(lopenummer.toByte()),
                SefrakObjektnummerDummy(x),
            )
        }
}

data class SefrakObjektnummerDummy(
    val value: String,
) : Comparable<SefrakObjektnummerDummy> {
    override fun compareTo(other: SefrakObjektnummerDummy) = value.compareTo(other.value)
}

fun sefrakId(value: Int): Id {
    return Id(Sefrakminne, value)
}

object Sefrakminne : IdType<Int, Sefrakminne> {
    override fun compareTo(other: Sefrakminne): Int {
        return 0
    }

    override fun compare(o1: Int, o2: Int): Int {
        return o1.compareTo(o2)
    }

    override val valueSerializer = Int.serializer()
}
