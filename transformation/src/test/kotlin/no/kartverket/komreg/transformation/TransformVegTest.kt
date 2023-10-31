package no.kartverket.komreg.transformation

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import no.kartverket.komreg.integration.spi.Ident
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TransformVegTest {
    private val ikrafttredelsesdato = LocalDate.now().toKotlinLocalDate()

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
                    til = listOf(Kommunenummer.Lopenummer(6), Kommunenummer.Lopenummer(7)),
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
                    ),
                ),
                adressekode = FraTil(
                    fra = Adressekode(2500),
                    til = Adressekode(2500),
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
        kommuner = emptyList(),
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
    fun `Transformasjon av identer skal endres dersom en enkel ident matcher`() {
        val entity = Entity(
            dummyId(123),
            identOfVeg(2, 5, 2500),
        )
        runBlocking {
            val result = identTransformer.transform(entity) { _, type ->
                idGenerator.idFor(type)
            }
            val expected1 = identOfVeg(3, 6, 2500)
            assertEquals(1, result!!.size)
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

private fun identOfVeg(fylkesnummer: Int, lopenummer: Int, adressekode: Int) = runBlocking {
    Ident(
        Fylkesnummer(fylkesnummer.toLong()),
        Kommunenummer.Lopenummer(lopenummer.toByte()),
        Adressekode(adressekode),
    )
}
