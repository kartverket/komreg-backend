package no.kartverket.komreg.transformation.rule

import arrow.core.*
import com.google.common.collect.BoundType
import com.google.common.collect.ImmutableRangeSet
import com.google.common.collect.Range
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldMatchInOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.matrikkel.domain.domain
import no.kartverket.komreg.transformation.NoTransform
import no.kartverket.komreg.transformation.ValueTransform
import no.kartverket.komreg.transformation.error.ConflictingTargetValue
import no.kartverket.komreg.transformation.error.MissingComponentValue
import no.kartverket.komreg.transformation.error.RuleError

class SplitRuleTest : BehaviorSpec({
    given("Some rules for kommunenummer (1 -> 11, [2,3] -> 12)") {
        val knrRule1 = Specific(
            Kommunenummer.Lopenummer.domain,
            Kommunenummer.Lopenummer(1), Kommunenummer.Lopenummer(11)
        )
        val knrRule2 = Specific(
            Kommunenummer.Lopenummer.domain,
            Kommunenummer.Lopenummer(2), Kommunenummer.Lopenummer(12)
        )
        val knrRule3 = Specific(
            Kommunenummer.Lopenummer.domain,
            Kommunenummer.Lopenummer(3), Kommunenummer.Lopenummer(12)
        )

        and("a split rule for fylkesnummer (9 -> 99) for these kommunenummer rules") {
            val sourceFylkesnummer = Fylkesnummer(9)
            val targetFylkesnummer = Fylkesnummer(89)

            val aSplitRule = Split(
                Fylkesnummer.domain,
                sourceFylkesnummer,
                nonEmptySetOf(
                    SplitOrAdjust.SplitEntry(knrRule1, targetFylkesnummer),
                    SplitOrAdjust.SplitEntry(knrRule2, targetFylkesnummer),
                    SplitOrAdjust.SplitEntry(knrRule3, targetFylkesnummer)
                )
            ).shouldBeRight().toNonCopy()

            then("it should not be possible to combine with a rule that has a conflicting target value for one of its kommunenummer rules") {
                val otherFylkesnummer = Fylkesnummer(88)
                val otherSplitRule = Split(
                    Fylkesnummer.domain,
                    sourceFylkesnummer,
                    nonEmptySetOf(
                        SplitOrAdjust.SplitEntry(knrRule1, targetFylkesnummer),
                        SplitOrAdjust.SplitEntry(knrRule2, otherFylkesnummer),
                        SplitOrAdjust.SplitEntry(knrRule3, targetFylkesnummer)
                    )
                ).shouldBeRight().toNonCopy()

                val result = aSplitRule + otherSplitRule
                val err = result.shouldBeLeft().shouldBeInstanceOf<ConflictingTargetValue<Kommunenummer.Lopenummer, Fylkesnummer>>()

                err.conflictRanges.asMapOfRanges().values.shouldForAll { targetRanges ->
                    targetRanges.asRanges().shouldForAll { targetRange -> targetRange.contains(targetFylkesnummer) || targetRange.contains(otherFylkesnummer) }
                }
            }

            then("two different ways to create the rule should be associative") {
                val splitRule1 = Split.invoke(
                    Fylkesnummer.domain,
                    sourceFylkesnummer,
                    nonEmptySetOf(SplitOrAdjust.SplitEntry(knrRule1, targetFylkesnummer))
                ).shouldBeRight()
                val splitRule2= Split.invoke(
                    Fylkesnummer.domain,
                    sourceFylkesnummer,
                    nonEmptySetOf(SplitOrAdjust.SplitEntry(knrRule2, targetFylkesnummer), SplitOrAdjust.SplitEntry(knrRule3, targetFylkesnummer))
                ).shouldBeRight()

                val splitRule3= Split.invoke(
                    Fylkesnummer.domain,
                    sourceFylkesnummer,
                    nonEmptySetOf(SplitOrAdjust.SplitEntry(knrRule3, targetFylkesnummer))
                ).shouldBeRight()

                val sum1 = (splitRule1 + splitRule2 + splitRule3).shouldBeRight().toNonCopy()

                sum1.shouldBe(aSplitRule)
            }


        }
    }

    given(""""at man prøver å splitte et fylke i tre deler, der:
        knr < 10 går til fnr 21,
        knr 2..4 går til fnr 22, og 
        knr 6..8 går til fnr 23""".replace('\n', ';')) {
        val fylkeSomSkalSplittes = Fylkesnummer(11)
        val knrLt10ToFnr21 = SplitOrAdjust.SplitEntry(
            Increment(
                Kommunenummer.Lopenummer.domain,
                ImmutableRangeSet.of(Range.upTo(Kommunenummer.Lopenummer(10), BoundType.OPEN)), 0
            ),
            Fylkesnummer(21)
        )

        val knr2until5toFnr22 = SplitOrAdjust.SplitEntry(
            Increment(
                Kommunenummer.Lopenummer.domain,
                ImmutableRangeSet.of(Range.closed(Kommunenummer.Lopenummer(2), Kommunenummer.Lopenummer(4))), 0
            ),
            Fylkesnummer(22)
        )

        val knr6until9toFnr23 = SplitOrAdjust.SplitEntry(
            Increment(
                Kommunenummer.Lopenummer.domain,
                ImmutableRangeSet.of(Range.closed(Kommunenummer.Lopenummer(6), Kommunenummer.Lopenummer(8))), 0
            ),
            Fylkesnummer(23)
        )

        val invalidSplitEntries = nonEmptySetOf(knrLt10ToFnr21, knr2until5toFnr22, knr6until9toFnr23)


        then("skal det feile med at konflikt i målverdier") {
            val split = Split(
                Fylkesnummer.domain,
                fylkeSomSkalSplittes,
                invalidSplitEntries
            )
            val errValue = split.shouldBeLeft()

            errValue.shouldBeInstanceOf<ConflictingTargetValue<*, *>>()

            errValue.conflictRanges.asMapOfRanges().entries.shouldForAll { (_, targetValues) ->
                targetValues.asRanges().shouldForAll { targetRange ->
                    targetRange.shouldBeInstanceOf<Range<Fylkesnummer>>()
                        .intersection(Range.closed(Fylkesnummer(21), Fylkesnummer(23)))
                        .should { !it.isEmpty }
                }

            }
        }

    }

    val knrLt10toFnr21 = SplitOrAdjust.SplitEntry(
        Increment(
            Kommunenummer.Lopenummer.domain,
            ImmutableRangeSet.of(Range.upTo(Kommunenummer.Lopenummer(10), BoundType.OPEN)), 0
        ),
        Fylkesnummer(21)
    )

    val mergeKnr10until15to99 = (10 until 20)
        .map { Kommunenummer.Lopenummer(it.toByte()) }
        .map { Specific(Kommunenummer.Lopenummer.domain, it, Kommunenummer.Lopenummer(99)) }
        .reduceOrNull<Specific<Kommunenummer.Lopenummer>, Either<RuleError, ComponentRule<Kommunenummer.Lopenummer>>>({it.right()}) { acc, rule ->
            acc.flatMap { it + rule }
        }!!
        .shouldBeRight()
        .toNonCopy()
        .shouldBeInstanceOf<Merge<Kommunenummer.Lopenummer>>()

    val knr10until15toFnr22 = SplitOrAdjust.SplitEntry(
        mergeKnr10until15to99,
        Fylkesnummer(22)
    )

    val knr15until20toFnr22 = (15 until 20)
        .map { Kommunenummer.Lopenummer(it.toByte()) }
        .map { Specific(Kommunenummer.Lopenummer.domain, it, Kommunenummer.Lopenummer(99)) }
        .map { SplitOrAdjust.SplitEntry(it, Fylkesnummer(22)) }
        .toTypedArray()

    val someSplitEntries = nonEmptySetOf(knrLt10toFnr21, knr10until15toFnr22, *knr15until20toFnr22)

    given("a split rule for fylkesnummer 11 where knr < 10 goes to fnr 21 and knr >= 10 < 20 goes to fnr 22") {
        val splitRule = Split(
            Fylkesnummer.domain,
            Fylkesnummer(11),
            someSplitEntries
        ).shouldBeRight()

        and("an other split rule for fylkesnummer 11 where knr > 20 goes to fnr 23") {
            val knrGt20toFnr23 = SplitOrAdjust.SplitEntry(
                Increment(
                    Kommunenummer.Lopenummer.domain,
                    ImmutableRangeSet.of(Range.downTo(Kommunenummer.Lopenummer(20), BoundType.OPEN)), 0
                ),
                Fylkesnummer(23)
            )
            val other = Split(Fylkesnummer.domain, Fylkesnummer(11), nonEmptySetOf(knrGt20toFnr23)).shouldBeRight()

            then("the the rules should compose into a split rule with all entries") {
                val composed = (splitRule + other)
                    .shouldBeRight()
                    .toNonCopy()
                    .shouldBeInstanceOf<Split<Fylkesnummer>>()

                val rangeMap = composed
                    .ruleMap[Kommunenummer.Lopenummer::class]
                    .shouldNotBeNull()

                val componentRules = rangeMap.asMapOfRanges().values
                componentRules.map { it.targetValue }
                    .shouldContainAll(Fylkesnummer(21), Fylkesnummer(22), Fylkesnummer(23))
            }

        }

        `when`("a composite value with fylkesnummer 10 is transformed") {
            then("it should not be transformed") {
                val composite = listOf(Fylkesnummer(10), Kommunenummer.Lopenummer(5))
                val result = splitRule.transform(composite).shouldBeRight()

                result.shouldForAll { it.shouldBeInstanceOf<NoTransform<*>>() }
            }
        }

        `when`("a composite value with fylkesnummer 11") {
            and("kommuneløpenummer 5 is transformed") {
                val composite = listOf(Fylkesnummer(11), Kommunenummer.Lopenummer(5))
                then("it should be transformed to fylkesnummer 21") {
                    val result = splitRule.transform(composite).shouldBeRight()
                    result.shouldMatchInOrder({
                        it.shouldBeTypeOf<ValueTransform<Fylkesnummer>>()
                        it.targetValue shouldBe Fylkesnummer(21)
                    }, {
                        it.shouldBeTypeOf<ValueTransform<Kommunenummer.Lopenummer>>()
                        it.targetValue.shouldBe(Kommunenummer.Lopenummer(5))
                    }
                    )
                }
            }
            and("kommuneløpenummer 10 is transformed") {
                val composite = listOf(Fylkesnummer(11), Kommunenummer.Lopenummer(10))
                then("it should be transformed to fylkesnummer 22") {
                    val result = splitRule.transform(composite).shouldBeRight()
                    result.shouldMatchInOrder({
                        it.shouldBeTypeOf<ValueTransform<Fylkesnummer>>()
                        it.targetValue shouldBe Fylkesnummer(22)
                    }, {
                        it.shouldBeTypeOf<ValueTransform<Kommunenummer.Lopenummer>>()
                        it.targetValue.shouldBe(mergeKnr10until15to99.targetValue)
                    }
                    )
                }
            }

            and("kommuneløpenummer 20 is transformed") {
                val composite = listOf(Fylkesnummer(11), Kommunenummer.Lopenummer(20))
                then("it should fail with missing component value") {
                    val result = splitRule.transform(composite).shouldBeLeft()
                    result.shouldBeInstanceOf<MissingComponentValue<*>>()
                    result.splitRule.shouldContainExactly(splitRule)
                }
            }


        }
    }

    given("a adjust rule for fylkesnummer 11 where knr < 10 goes to fnr 21 and knr >= 10 < 20 goes to fnr 22") {
        val adjust = Adjust(
            Fylkesnummer.domain,
            Fylkesnummer(11),
            someSplitEntries
        ).shouldBeRight()

        `when`("a composite value with fylkesnummer 11") {
            and("kommuneløpenummer 5 is transformed") {
                val composite = listOf(Fylkesnummer(11), Kommunenummer.Lopenummer(5))
                then("it should be transformed to fylkesnummer 21") {
                    val result = adjust.transform(composite).shouldBeRight()
                    result.shouldMatchInOrder({
                        it.shouldBeTypeOf<ValueTransform<Fylkesnummer>>()
                        it.targetValue shouldBe Fylkesnummer(21)
                    }, {
                        it.shouldBeTypeOf<ValueTransform<Kommunenummer.Lopenummer>>()
                        it.targetValue.shouldBe(Kommunenummer.Lopenummer(5))
                    }
                    )
                }
            }


            and("kommuneløpenummer 20 is transformed") {
                val composite = listOf(Fylkesnummer(11), Kommunenummer.Lopenummer(20))
                then("it should succeed with no transform") {
                    adjust.transform(composite).shouldBeRight().shouldForAll {
                        it.shouldBeInstanceOf<NoTransform<*>>()
                    }
                }
            }


        }
    }

    given("an uncountable domain") {
        // Ikke realistisk at kretstype endres fra S til S1/S2, men dette er bare for å teste at rule og domain spiller på lag
        val rule = Split(
            Kretstype.domain,
            Kretstype("S"),
            listOf(
                SplitOrAdjust.SplitEntry(
                    Specific(Kretsnummer.domain, Kretsnummer(1), Kretsnummer(1)),
                    Kretstype("S1")
                ),
                SplitOrAdjust.SplitEntry(
                    Specific(Kretsnummer.domain, Kretsnummer(2), Kretsnummer(1)),
                    Kretstype("S2")
                ),
            )
        ).shouldBeRight()

        then ("Split rule should work") {
            val result = rule.transform(listOf(Kretstype("S"), Kretsnummer(1))).shouldBeRight()
            result.shouldHaveSize(2).toList()[0]
                .shouldBeInstanceOf<ValueTransform<Kretstype>>()
                .targetValue shouldBe Kretstype("S1")
        }
    }

})
