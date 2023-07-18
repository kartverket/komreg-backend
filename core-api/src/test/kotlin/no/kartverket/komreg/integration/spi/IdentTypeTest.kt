package no.kartverket.komreg.integration.spi

import io.kotest.core.spec.style.FunSpec
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import org.junit.jupiter.api.Assertions.*

class IdentTypeTest : FunSpec({
    test("can append from 1 to 2") {
        val type1 = identTypeOf1<Fylkesnummer>()
        val type2 = type1.append<Kommunenummer.Lopenummer>()

        assertEquals(1, type1.types.size, "type1.types.size")
        assertEquals(2, type2.types.size, "type2.types.size")
        assertEquals(type1.types, type2.types.subList(0, 1), "type1 ⊆ type2")
    }

    test("can append from 2 to 3") {
        val type1 = identTypeOf2<Fylkesnummer, Kommunenummer.Lopenummer>()
        val type2 = type1.append<String>()

        assertEquals(2, type1.types.size, "type1.types.size")
        assertEquals(3, type2.types.size, "type2.types.size")
        assertEquals(type1.types, type2.types.subList(0, 2), "type1 ⊆ type2")
    }
})
