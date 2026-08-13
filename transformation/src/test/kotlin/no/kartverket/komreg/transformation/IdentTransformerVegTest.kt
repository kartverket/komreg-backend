package no.kartverket.komreg.transformation

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.parameter.compat.Parameters
import no.kartverket.komreg.parameter.data.HList
import no.kartverket.komreg.parameter.data.times
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import no.kartverket.komreg.core.domain.Fylkesnummer as Fylke
import no.kartverket.komreg.core.domain.Kommunenummer.Lopenummer as Kommune

class IdentTransformerImplVegTest {
    private val ikrafttredelsesdato = LocalDate.now().toKotlinLocalDate()

    private val reguleringsInput =
        Reguleringsinput(
            id = "123",
            ikrafttredelsesdato = ikrafttredelsesdato,
            endringer =
            listOf(
                Kommuneendring(
                    fylkesnummer =
                    FraEnTilMange(
                        fra = Fylke(2),
                        til = listOf(Fylke(3)),
                    ),
                    kommuneløpenummer =
                    FraEnTilMange(
                        fra = Kommune(5),
                        til = listOf(Kommune(6), Kommune(7)),
                    ),
                ),
                Vegendring(
                    fylkesnummer =
                    FraEnTilMange(
                        fra = Fylke(2),
                        til = listOf(Fylke(3)),
                    ),
                    kommuneløpenummer =
                    FraEnTilMange(
                        fra = Kommune(5),
                        til =
                        listOf(
                            Kommune(6),
                        ),
                    ),
                    adressekode =
                    FraEnTilMange(
                        fra = Adressekode(2500),
                        til =
                        listOf(
                            Adressekode(2500),
                        ),
                    ),
                ),
                Vegendring(
                    fylkesnummer =
                    FraEnTilMange(
                        fra = Fylke(2),
                        til = listOf(Fylke(3)),
                    ),
                    kommuneløpenummer =
                    FraEnTilMange(
                        fra = Kommune(5),
                        til =
                        listOf(
                            Kommune(6),
                            Kommune(7),
                        ),
                    ),
                    adressekode =
                    FraEnTilMange(
                        fra = Adressekode(2600),
                        til =
                        listOf(
                            Adressekode(2600),
                        ),
                    ),
                ),
            ),
            kommuner = listOf(dummyKommune(3, 6), dummyKommune(3, 7)),
            fylker = emptyList(),
        )
    private val mappings = runBlocking { mapInput(reguleringsInput) }
    private fun identTransformer() = Parameters {
        adjust(Fylke(2)) {
            split(Kommune(5)) {
                to(HList * Fylke(3) * Kommune(6), dummyKommunedata())
                move(Adressekode(2500), HList * Fylke(3) * Kommune(6) * Adressekode(2500))
                to(HList * Fylke(3) * Kommune(7), dummyKommunedata())
                split(Adressekode(2600)) {
                    to(HList * Fylke(3) * Kommune(6) * Adressekode(2600), Unit)
                    move(Adressenummernummer(1), HList * Fylke(3) * Kommune(6) * Adressekode(2600) * Adressenummernummer(1))
                    to(HList * Fylke(3) * Kommune(7) * Adressekode(2600), Unit)
                    move(Adressenummernummer(2), HList * Fylke(3) * Kommune(7) * Adressekode(2600) * Adressenummernummer(2))
                }
            }
        }
    }

    private val idGenerator = mockk<IdGeneratorManager>()

    @BeforeEach
    fun setup() {
        coEvery { idGenerator.idFor(any(), any() as? Any?) } returns dummyId(1)
    }

    @Test
    fun `Transformasjon av identer skal endres dersom en enkel ident matcher`() {
        val entity =
            Entity(
                dummyId(123),
                identOfVeg(2, 5, 2500),
            )
        runBlocking {
            val result = identTransformer().transform(entity, idGenerator::idFor)
            val expected1 = identOfVeg(3, 6, 2500)
            assertEquals(1, result!!.size)
            assertEquals(expected1, result[0].transformedIdent)
        }
    }

    @Test
    fun `Transformasjon av identer skal endres dersom flere identer matcher`() {
        val entity =
            Entity(
                dummyId(123),
                identOfVeg(2, 5, 2600),
            )
        runBlocking {
            val result = identTransformer().transform(entity, idGenerator::idFor)
            val expected1 = identOfVeg(3, 6, 2600)
            val expected2 = identOfVeg(3, 7, 2600)
            assertEquals(expected1, result!![0].transformedIdent)
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
        Fylke(fylkesnummer.toLong()),
        Kommune(lopenummer.toByte()),
        Adressekode(adressekode),
    )
}
