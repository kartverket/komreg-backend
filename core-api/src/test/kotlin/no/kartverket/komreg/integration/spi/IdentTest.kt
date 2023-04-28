package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IdentTest {
    @Test
    fun createAndGet() {
        val ident = Ident(
            Fylkesnummer(98),
            Kommunenummer.Lopenummer(76),
        )

        assertEquals(Fylkesnummer(98), ident.get<Fylkesnummer>())
        assertEquals(Kommunenummer.Lopenummer(76), ident.get<Kommunenummer.Lopenummer>())
    }

    @Test
    fun duplicatesNotAllowed() {
        val e = assertThrows<IllegalStateException> {
            Ident(
                Fylkesnummer(99),
                Fylkesnummer(98),
            )
        }
        assertEquals("Duplicate key class no.kartverket.komreg.core.domain.Fylkesnummer (attempted merging values Fylkesnummer(value=99) and Fylkesnummer(value=98))", e.message)
    }

    @Test
    fun cantTransformNonExistingValues() {
        val ident = Ident(
            Fylkesnummer(99)
        )
        val transformation = Ident(
            Kommunenummer.Lopenummer(99)
        )
        val e = assertThrows<IllegalArgumentException> {
            ident.transform(transformation)
        }
        assertEquals("Can not transform non-existing values: [class no.kartverket.komreg.core.domain.Kommunenummer\$Lopenummer]", e.message)
    }
}