package no.kartverket.komreg.transformation

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TransformEntityTest {
    private val dummyKommune = Kommune(
        kommunenummer = Kommunenummer(
            Fylkesnummer(3),
            Kommunenummer.Lopenummer(6),
        ),
        kommunenavn = Kommunenavn("Dummy"),
        koordinatsystem = Koordinatsystem.UTM32,
        senterpunkt = Koordinat(123.0, 456.0),
        nedsattKonsesjonsgrense = false,
        godkjenteGardsnumre = "1,2,3",
        gyldigTilDato = null,
        adresse = null,
        standardRekvirent = null,
        kommunevapen = null,
    )

    private val reguleringsInput = Reguleringsinput(
        id = "123",
        ikrafttredelsesdato = LocalDate.now().toKotlinLocalDate(),
        endringer = listOf(
            Kommuneendring(
                fylkesnummer = FraTil(
                    fra = Fylkesnummer(2),
                    til = Fylkesnummer(3),
                ),
                kommuneløpenummer = FraEnTilMange(
                    fra = Kommunenummer.Lopenummer(5),
                    til = listOf(Kommunenummer.Lopenummer(6)),
                ),
            ),
            Vegendring(
                fylkesnummer = FraTil(
                    fra = Fylkesnummer(2),
                    til = Fylkesnummer(3),
                ),
                kommuneløpenummer = FraEnTilMange(
                    fra = Kommunenummer.Lopenummer(5),
                    til = listOf(
                        Kommunenummer.Lopenummer(6),
                        Kommunenummer.Lopenummer(7),
                    ),
                ),
                adressekode = FraTil(
                    fra = Adressekode(2600),
                    til = Adressekode(2600),
                ),
            ),
        ),
        kommuner = listOf(dummyKommune),
        fylker = emptyList(),
    )

    private val mappings = reguleringsInput.toMappings()
    private val identTransformer = IdentTransformer(*mappings.toTypedArray())

    private val idGenerator = mockk<IdGeneratorManager>()

    @BeforeEach
    fun setup() {
        every { idGenerator.idFor(any()) } returns dummyId(1)
    }

    @Test
    fun `Flytting av kommune skal returnere to transformations, en for utgått og en ny`() {
        val entity = Entity(
            dummyId(123),
            identOfKommune(2, 5)
        )
        runBlocking {
            val result = identTransformer.transform(entity) { _, type ->
                idGenerator.idFor(type)
            }
            val expected = identOfKommune(3, 6)
            // TODO Sjekk på payload/resultObject
            assertEquals(null, result!![0].resultObject)
            assertEquals(dummyKommune, result[1].resultObject)
            assertEquals(expected, result[1].transformedIdent)
        }
    }

    @Test
    fun `A transformation of idents should not transform entity when unmatched idents`() {
        val entity = Entity(
            dummyId(123),
            identOfKommune(10, 50)
        )
        runBlocking {
            val result = identTransformer.transform(entity) { _, type ->
                idGenerator.idFor(type)
            }
            assertEquals(null, result)
        }
    }

    @Test
    fun `A transformation of associated idents should change all matching idents`() {
        val entity = Entity(
            dummyId(123),
            associatedIdents = setOf(
                identOfMatrikkelenhet(2, 5, 1),
                identOfMatrikkelenhet(2, 5, 2),
            ),
        )
        runBlocking {
            val result = identTransformer.transform(entity) { _, type ->
                idGenerator.idFor(type)
            }
            val expected = setOf(
                identOfMatrikkelenhet(3, 6, 1),
                identOfMatrikkelenhet(3, 6, 2),
            )
            assertEquals(expected, result?.single()?.transformedAssociatedIdents)
        }
    }

    @Test
    fun `A transformation of associated idents should only change matching idents`() {
        val entity = Entity(
            dummyId(123),
            associatedIdents = setOf(
                identOfMatrikkelenhet(2, 5, 1),
                identOfMatrikkelenhet(10, 15, 1),
            ),
        )
        runBlocking {
            val result = identTransformer.transform(entity) { _, type ->
                idGenerator.idFor(type)
            }
            val expected = setOf(
                identOfMatrikkelenhet(3, 6, 1),
                identOfMatrikkelenhet(10, 15, 1),
            )
            assertEquals(expected, result?.single()?.transformedAssociatedIdents)
        }
    }

    @Test
    fun `A transformation of associated idents should only change matching idents (non-matching ident first)`() {
        val entity = Entity(
            dummyId(123),
            associatedIdents = setOf(
                identOfMatrikkelenhet(10, 15, 1),
                identOfMatrikkelenhet(2, 5, 1),
            ),
        )
        runBlocking {
            val result = identTransformer.transform(entity) { _, type ->
                idGenerator.idFor(type)
            }
            val expected = setOf(
                identOfMatrikkelenhet(10, 15, 1),
                identOfMatrikkelenhet(3, 6, 1),
            )
            assertEquals(expected, result?.single()?.transformedAssociatedIdents)
        }
    }

    @Test
    fun `A transformation of idents should change when multiple idents matches`() {
        val entity = Entity(
            dummyId(123),
            identOfVeg(2, 5, 2600)
        )
        runBlocking {
            val result = identTransformer.transform(entity) { _, type ->
                idGenerator.idFor(type)
            }
            val expected1 = identOfVeg(3, 6, 2600)
            val expected2 = identOfVeg(3, 7, 2600)
            assertEquals(expected1, result!![0].transformedIdent)
            assertEquals(expected2, result[1].transformedIdent)
        }
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

private fun identOfKommune(fylkesnummer: Int, lopenummer: Int) = runBlocking {
    Ident(Fylkesnummer(fylkesnummer.toLong()), Kommunenummer.Lopenummer(lopenummer.toByte()))
}

private fun identOfVeg(fylkesnummer: Int, lopenummer: Int, adressekode: Int) = runBlocking {
    Ident(
        Fylkesnummer(fylkesnummer.toLong()),
        Kommunenummer.Lopenummer(lopenummer.toByte()),
        Adressekode(adressekode),
    )
}
