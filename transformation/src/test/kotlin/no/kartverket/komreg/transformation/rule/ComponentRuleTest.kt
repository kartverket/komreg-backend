package no.kartverket.komreg.transformation.rule

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeSingleton
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.matrikkel.domain.domain

class ComponentRuleTest : BehaviorSpec({
    given("some rules") {
        val x = Specific(Fylkesnummer.domain, Fylkesnummer(11), Fylkesnummer(21))
        val y = Specific(Fylkesnummer.domain, Fylkesnummer(12), Fylkesnummer(21))
        val z = Specific(Fylkesnummer.domain, Fylkesnummer(13), Fylkesnummer(23))
        then("addition of these rules should be commutative (if successful)") {
            val sum = (x + y + z).shouldBeRight()
            val b = (x + z + y).shouldBeRight()
            val c = (y + x + z).shouldBeRight()
            val d = (y + z + x).shouldBeRight()
            val e = (z + x + y).shouldBeRight()
            val f = (z + y + x).shouldBeRight()

            setOf(sum, b, c, d, e, f).shouldBeSingleton()
        }
    }

})