package no.kartverket.komreg.transformation.rule

import arrow.core.Either
import arrow.core.bisequenceNullable
import arrow.core.raise.either
import arrow.core.reduceOrNull
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.matrikkel.domain.domain
import no.kartverket.komreg.transformation.Kommuneendring
import no.kartverket.komreg.transformation.ValueTransform
import no.kartverket.komreg.transformation.error.RuleError

class ComponentRuleTest : BehaviorSpec({
    given("en regeuleringsinput") {
        val kommuneendrings = listOf(
            Kommuneendring(Kommunenummer(1111), Kommunenummer(2221)),
            Kommuneendring(Kommunenummer(1112), Kommunenummer(2222)),
            Kommuneendring(Kommunenummer(1113), Kommunenummer(2223)),
            Kommuneendring(Kommunenummer(1114), Kommunenummer(2224)),
        )

        val sum = kommuneendrings
            .map<Kommuneendring, ComponentRule<Kommunenummer.Lopenummer>> {
                Specific(Kommunenummer.Lopenummer.domain, it.fra.lopenummer, it.til.lopenummer)
            }
            .reduceOrNull<ComponentRule<Kommunenummer.Lopenummer>, Either<RuleError, ComponentRule<Kommunenummer.Lopenummer>>>(
                { it.right() }) { acc, rule ->
                either {
                    val sum = (acc.bind() + rule)
                    sum.bind()
                }
            }
            .shouldNotBeNull()
            .shouldBeRight()

        then("summen av disse reglene skal være en regel") {
            val x = sum.transform(listOf(Kommunenummer.Lopenummer(11))).shouldBeRight()
            val transformed = x.shouldBeSingleton()
            val ttt = transformed.single().shouldBeTypeOf<ValueTransform<Kommunenummer.Lopenummer>>()
            ttt.targetValue.shouldBe(Kommunenummer.Lopenummer(21))
        }
    }

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