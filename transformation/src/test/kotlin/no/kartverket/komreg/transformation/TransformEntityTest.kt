package no.kartverket.komreg.transformation

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import no.kartverket.komreg.integration.spi.Ident
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
        val entity = Entity(dummyId(123), identOf(2, 5))
        val result = transformerEntity(reguleringsInput, entity, idGenerator)
        val expected = identOf(3, 6)
        kotlin.test.assertEquals(expected, result?.single()?.transformedIdent)
    }

    @Test
    fun `A transformation of idents should not transform entity when unmatched idents`() {
        val entity = Entity(dummyId(123), identOf(10, 50))
        val result = transformerEntity(reguleringsInput, entity, idGenerator)
        kotlin.test.assertEquals(null, result)
    }

    @Test
    fun `A transformation of associated idents should change all matching idents`() {
        val entity = Entity(
            dummyId(123),
            associatedIdents = setOf(
                identOf(2, 5, 1),
                identOf(2, 5, 2),
            ),
        )
        val result = transformerEntity(reguleringsInput, entity, idGenerator)
        val expected = setOf(
            identOf(3, 6, 1),
            identOf(3, 6, 2),
        )

        kotlin.test.assertEquals(expected, result?.single()?.transformedAssociatedIdents)
    }

    @Test
    fun `A transformation of associated idents should only change matching idents`() {
        val entity = Entity(
            dummyId(123),
            associatedIdents = setOf(
                identOf(2, 5, 1),
                identOf(10, 15, 1),
            ),
        )
        val result = transformerEntity(reguleringsInput, entity, idGenerator)
        val expected = setOf(
            identOf(3, 6, 1),
            identOf(10, 15, 1),
        )
        kotlin.test.assertEquals(expected, result?.single()?.transformedAssociatedIdents)
    }
}

private fun identOf(fylkesnummer: Int, lopenummer: Int, gardsnummer: Int? = null) =
    runBlocking {
        if (gardsnummer != null) {
            Ident(
                Fylkesnummer(fylkesnummer.toLong()),
                Kommunenummer.Lopenummer(lopenummer.toByte()),
                Matrikkelnummer.Gardsnummer(gardsnummer),
            )
        } else {
            Ident(
                Fylkesnummer(fylkesnummer.toLong()),
                Kommunenummer.Lopenummer(lopenummer.toByte()),
            )
        }
    }
