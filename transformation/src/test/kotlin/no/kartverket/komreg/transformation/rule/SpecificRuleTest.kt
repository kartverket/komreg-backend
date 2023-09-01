package no.kartverket.komreg.transformation.rule

import com.google.common.collect.ImmutableRangeSet
import com.google.common.collect.Range
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.matrikkel.domain.domain
import no.kartverket.komreg.transformation.error.ConflictingTargetValue

class SpecificRuleTest : BehaviorSpec({
    given("a specific rule") {
        val specific = Specific(Fylkesnummer.domain, Fylkesnummer(11), Fylkesnummer(21))
        then("it should be able to return a copy") {
            val copy = specific.asCopy(Fylkesnummer.domain).shouldNotBeNull()
            copy.domain shouldBe Fylkesnummer.domain
            copy.backingRule shouldBe specific
        }
        then("it should be equal to equal rules") {
            val other = Specific(specific.domain, specific.sourceValue, specific.targetValue)
            specific shouldBe other
        }
        and("an other specific rule") {
            `when`("the other rule has different source and target value") {
                val b = Specific(Fylkesnummer.domain, Fylkesnummer(12), Fylkesnummer(22))
                then("the rules should compose into a switch rule between the two") {
                    (specific + b).map { it.toNonCopy() } shouldBeRight Switch(Fylkesnummer.domain, specific, b).shouldBeRight()
                }
            }
            `when`("the other rule has the same source and a different target value") {
                val b = Specific(Fylkesnummer.domain, specific.sourceValue, Fylkesnummer(22))
                then("the rules should not compose") {
                    val error = (specific + b).shouldBeLeft()
                    error as ConflictingTargetValue<*,*>
                    error.rules.map { (it as ComponentRule<*>).toNonCopy() } shouldBe (setOf(specific, b))
                }
            }
            `when`("the other rule has the same target and a different source value") {
                val b = Specific(Fylkesnummer.domain, Fylkesnummer(12), specific.targetValue)
                then("the rules should not compose into a merge rule") {
                    val merge = (specific + b)
                        .shouldBeRight()
                        .toNonCopy()
                        .shouldBeTypeOf<Merge<Fylkesnummer>>()

                    merge.sourceRanges shouldBe specific.sourceRanges.union(b.sourceRanges)
                    merge.targetValue shouldBe specific.targetValue
                    merge.explicitRules shouldBe setOf(specific, b)
                }
            }
            `when`("the other rule has has the same source and target value") {
                val b = Specific(Fylkesnummer.domain, specific.sourceValue, specific.targetValue)
                then("the rules should compose into a single specific rule") {
                    (specific + b).shouldBeRight().toNonCopy() shouldBe specific
                }
            }
        }

        and("a increment rule") {
            `when`("the increment rule has the same target value as the specific rule") {
                val increment = Increment(
                    Fylkesnummer.domain,
                    specific.sourceRanges.union(ImmutableRangeSet.of(Range.singleton(Fylkesnummer(13)))),
                    Fylkesnummer.domain.distance(specific.sourceValue, specific.targetValue))
                then("the rules should compose into a increment rule") {
                    val composed = (specific + increment).shouldBeRight().toNonCopy()
                    composed.shouldBeTypeOf<Increment<Fylkesnummer>>()
                    composed.sourceRanges.shouldBe(specific.sourceRanges.union(increment.sourceRanges))
                }
            }
            `when`("the increment rule has a different target value than the specific rule") {
                val increment = Increment(
                    Fylkesnummer.domain,
                    specific.sourceRanges.union(ImmutableRangeSet.of(Range.singleton(Fylkesnummer(13)))),
                    Fylkesnummer.domain.distance(specific.sourceValue, specific.targetValue) - 1)
                then("the rules should not compose") {
                    val error = (specific + increment).shouldBeLeft()
                    error.shouldBeTypeOf<ConflictingTargetValue<*,*>>()

                    error.rules.map { (it as ComponentRule<*>).toNonCopy() } shouldBe (setOf(specific, increment))
                }
            }
        }
    }
})