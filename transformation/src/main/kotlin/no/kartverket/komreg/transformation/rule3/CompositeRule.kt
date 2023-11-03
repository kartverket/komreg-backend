package no.kartverket.komreg.transformation.rule3

import arrow.core.raise.Raise
import no.kartverket.komreg.transformation.rule3.error.RuleError
import no.kartverket.komreg.transformation.rule3.error.TextRuleError
import no.kartverket.komreg.transformation.rule3.range.Range
import no.kartverket.komreg.transformation.rule3.range.RangeMap
import no.kartverket.komreg.transformation.rule3.range.RangeSet
import no.kartverket.komreg.transformation.rule3.range.emptyRangeMap

sealed interface CompositeRule<A : Comparable<A>> : Rule.DirectOrComposite<A> {
    data class CreateOnly<A : Comparable<A>>(override val createRules: RangeMap<A, CreateRule<A>>) : CompositeRule<A>,
        Rule.DirectOrCompositeCreate<A> {
        override val rules: RangeMap<A, Nothing> get() = emptyRangeMap()

        override fun Raise<RuleError>.combine(
            other: Rule.DirectOrComposite<A>
        ): Rule.DirectOrComposite<A> {
            TODO()
        }

        fun combine(raise: Raise<RuleError>, other: Rule.DirectOrCompositeCreate<A>): Rule.DirectOrCompositeCreate<A> {
            val newCreateRules = when (other) {
                is CreateOnly -> {
                    other.createRules.fold(createRules) { acc, (otherRange, otherRule) ->
                        acc.plus(
                            otherRange,
                            otherRule
                        ) { conflictRange: RangeSet<A>, existingCreateRule, createRule ->
                            raise.raise(TextRuleError("Conflicting create rules for $conflictRange:\n\t$existingCreateRule\n\t$createRule"))
                        }
                    }
                }

                is CreateRule -> {
                    createRules.plus(
                        other.target,
                        other
                    ) { conflictRange: Range<A>, existingCreateRule, createRule ->
                        raise.raise(TextRuleError("Conflicting create rules for $conflictRange:\n\t$existingCreateRule\n\t$createRule"))
                    }

                }
            }
            return CreateOnly(newCreateRules)
        }
    }

    data class FromExistingOrCreate<A : Comparable<A>>(
        override val createRules: RangeMap<A, CreateRule<A>>,
        override val rules: RangeMap<A, Rule.DirectFromExisting<A>>
    ) : CompositeRule<A> {
        override fun Raise<RuleError>.combine(other: Rule.DirectOrComposite<A>): Rule.DirectOrComposite<A> {
            TODO("Not yet implemented")
        }
    }

    data class FromExistingOnly<A : Comparable<A>>(
        override val rules: RangeMap<A, Rule.DirectFromExisting<A>>
    ) : CompositeRule<A>, Rule.DirectOrCompositeFromExisting<A> {
        override val createRules: RangeMap<A, Nothing> get() = emptyRangeMap()
        override fun Raise<RuleError>.combine(other: Rule.DirectOrComposite<A>): Rule.DirectOrComposite<A> {
            TODO("Not yet implemented")
        }
    }

    val createRules: RangeMap<A, CreateRule<A>>
    val rules: RangeMap<A, Rule.DirectFromExisting<A>>
}