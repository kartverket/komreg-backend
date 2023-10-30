package no.kartverket.komreg.transformation

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import no.kartverket.komreg.integration.spi.Ident
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TransformVegadresseTest {
    private val ikrafttredelsesdato = LocalDate.now().toKotlinLocalDate()

    private val reguleringsInput = Reguleringsinput(
        id = "123",
        ikrafttredelsesdato = ikrafttredelsesdato,
        endringer = listOf(
            Vegadresseendring(
                fylkesnummer = FraTil(
                    fra = Fylkesnummer(2),
                    til = Fylkesnummer(3),
                ),
                kommuneløpenummer = FraTil(
                    fra = Kommunenummer.Lopenummer(5),
                    til = Kommunenummer.Lopenummer(6),
                ),
                adressekode = FraTil(
                    fra = Adressekode(2600),
                    til = Adressekode(2600),
                ),
                adressenummer = FraTil(
                    fra = Adressenummernummer(10),
                    til = Adressenummernummer(10),
                ),
                adressenummerbokstav = FraTil(
                    fra = Adressenummerbokstav('A'),
                    til = Adressenummerbokstav('A'),
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
    fun `A transformation of idents should change when a single ident matches`() {
        val entity = Entity(
            dummyId(123),
            identOfVegadresse(2, 5, 2600, 10, 'A'),
        )
        runBlocking {
            val result = identTransformer.transform(entity) { _, type ->
                idGenerator.idFor(type)
            }
            val expected1 = identOfVegadresse(3, 6, 2600, 10, 'A')
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
    adressebokstav: Char,
) =
    runBlocking {
        Ident(
            Fylkesnummer(fylkesnummer.toLong()),
            Kommunenummer.Lopenummer(lopenummer.toByte()),
            Adressekode(adressekode),
            Adressenummernummer(adressenummer),
            Adressenummerbokstav(adressebokstav),
        )
    }
