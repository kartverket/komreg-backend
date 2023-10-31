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
    private val ikrafttredelsesdato = LocalDate.now().toKotlinLocalDate()

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
        ikrafttredelsesdato = ikrafttredelsesdato,
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
    fun `Flytting av kommune skal returnere to transformasjoner, en for utgått og en for opprettelse av ny kommune`() {
        val entity = Entity(
            dummyId(123),
            identOfKommune(2, 5),
        )
        runBlocking {
            val result = identTransformer.transform(entity) { _, type ->
                idGenerator.idFor(type)
            }
            val expected = identOfKommune(3, 6)

            assertEquals(null, result!![0].resultObject)
            assertEquals(dummyKommune.tilKommunedata(ikrafttredelsesdato), result[1].resultObject)
            assertEquals(expected, result[1].transformedIdent)
        }
    }

    @Test
    fun `Transformasjon av identer skal ikke transformere entiteten når ingen av dens identer matcher`() {
        val entity = Entity(
            dummyId(123),
            identOfKommune(10, 50),
        )
        runBlocking {
            val result = identTransformer.transform(entity) { _, type ->
                idGenerator.idFor(type)
            }
            assertEquals(null, result)
        }
    }

    @Test
    fun `Transformasjon av assosierte identer skal endre alle identer den finner en match på`() {
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
    fun `Transformasjon av assosierte identer skal kun endre de identene den får match på, inneholder ident uten match`() {
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
    fun `Transformasjon av assosierte identer skal kun endre de identene den får match på, motsatt rekkefølge på ident uten match`() {
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

