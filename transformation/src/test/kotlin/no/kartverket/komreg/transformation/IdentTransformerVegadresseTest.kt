package no.kartverket.komreg.transformation

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.parameter.compat.Parameters
import no.kartverket.komreg.parameter.data.HList
import no.kartverket.komreg.parameter.data.times
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import no.kartverket.komreg.core.domain.Fylkesnummer as Fylke
import no.kartverket.komreg.core.domain.Kommunenummer.Lopenummer as Kommune

class IdentTransformerImplVegadresseTest {
    private val identTransformer = Parameters {
        adjust(Fylke(2)) {
            adjust(Kommune(5)) {
                adjust(Adressekode(2600)) {
                    move(
                        Adressenummernummer(10),
                        HList * Fylke(3) * Kommune(6) * Adressekode(2600) * Adressenummernummer(10)
                    )
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
    fun `A transformation of idents should change when a single ident matches`() {
        val entity = Entity(
            dummyId(123),
            identOfVegadresse(2, 5, 2600, 10),
        )
        runBlocking {
            val result = identTransformer.transform(entity, idGenerator::idFor)
            val expected1 = identOfVegadresse(3, 6, 2600, 10)
            assertEquals(1, result!!.size)
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
            Fylke(fylkesnummer.toLong()),
            Kommune(lopenummer.toByte()),
            Adressekode(adressekode),
            Adressenummernummer(adressenummer),
        )
    }
