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
    private val reguleringsInput = Reguleringsinput(
        id = "123",
        LocalDate.now().toKotlinLocalDate(),
        listOf(
            Kommuneendring(
                fylkesnummer = FraTil(
                    fra = Fylkesnummer(2),
                    til = Fylkesnummer(3),
                ),
                kommuneløpenummer = FraTil(
                    fra = Kommunenummer.Lopenummer(5),
                    til = Kommunenummer.Lopenummer(6),
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
        emptyList(),
        emptyList(),
    )

    private val idGenerator = mockk<IdGeneratorManager>()

    @BeforeEach
    fun setup() {
        every { idGenerator.idFor(any()) } returns dummyId(1)
    }

    @Test
    fun `A transformation of idents should change kommunenr based on input`() {
        val entity = Entity(dummyId(123), identOfKommune(2, 5))
        val result = transformerEntity(reguleringsInput, entity, idGenerator)
        val expected = identOfKommune(3, 6)
        assertEquals(expected, result?.single()?.transformedIdent)
    }

    @Test
    fun `A transformation of idents should not transform entity when unmatched idents`() {
        val entity = Entity(dummyId(123), identOfKommune(10, 50))
        val result = transformerEntity(reguleringsInput, entity, idGenerator)
        assertEquals(null, result)
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
        val result = transformerEntity(reguleringsInput, entity, idGenerator)
        val expected = setOf(
            identOfMatrikkelenhet(3, 6, 1),
            identOfMatrikkelenhet(3, 6, 2),
        )

        assertEquals(expected, result?.single()?.transformedAssociatedIdents)
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
        val result = transformerEntity(reguleringsInput, entity, idGenerator)
        val expected = setOf(
            identOfMatrikkelenhet(3, 6, 1),
            identOfMatrikkelenhet(10, 15, 1),
        )
        assertEquals(expected, result?.single()?.transformedAssociatedIdents)
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
        val result = transformerEntity(reguleringsInput, entity, idGenerator)
        val expected = setOf(
            identOfMatrikkelenhet(10, 15, 1),
            identOfMatrikkelenhet(3, 6, 1),
        )
        assertEquals(expected, result?.single()?.transformedAssociatedIdents)
    }

    @Test
    fun `A transformation of idents should change when multiple idents matches`() {
        val entity = Entity(dummyId(123), identOfVeg(2, 5, 2600))
        val result = transformerEntity(reguleringsInput, entity, idGenerator)
        val expected = listOf(identOfVeg(3, 6, 2600), identOfVeg(3, 7, 2600))
        assertEquals(expected, result?.map { it.transformedIdent })
    }

    @Test
    fun `A transformation of idents should change when regulering has single kommunenummer for vegendring`() {
        val vegendringMedEnTilKomunne = Vegendring(
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
                fra = Adressekode(2600),
                til = Adressekode(2600),
            ),
        )

        val nyRegInput = reguleringsInput.copy(
            endringer = reguleringsInput.endringer.map { endring ->
                if (endring is Vegendring) vegendringMedEnTilKomunne else endring
            },
        )

        val entity = Entity(dummyId(123), identOfVeg(2, 5, 2600))
        val result = transformerEntity(nyRegInput, entity, idGenerator)
        val expected = listOf(identOfVeg(3, 6, 2600))

        assertEquals(expected, result?.map { it.transformedIdent })
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
