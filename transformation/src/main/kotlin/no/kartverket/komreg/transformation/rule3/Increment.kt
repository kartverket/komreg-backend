package no.kartverket.komreg.transformation.rule3

import arrow.core.raise.Raise
import no.kartverket.komreg.transformation.rule3.error.RuleError
import no.kartverket.komreg.transformation.rule3.error.TextRuleError
import no.kartverket.komreg.transformation.rule3.range.*
import no.kartverket.komreg.transformation.rule3.util.PartiallyCombinable
import no.kartverket.komreg.transformation.rule3.util.combine

data class Increment<A : Comparable<A>>(
    override val source: Range<A>,
    val increment: Long,
    override val subRules: SubRuleMap = emptySubRuleMap()
) : Rule.DirectFromExistingAndKeep<A>() {

    context (Raise<RuleError>)
    override fun combine(other: Rule.DirectFromExisting<A>): Rule.DirectOrCompositeFromExisting<A> {
        return when (other) {
            is Increment -> {
                val conflictRange = source rangeIntersect other.source
                if (conflictRange == null) {
                    combineAsComposite(other)
                } else {
                    if (increment != other.increment) {
                        raise(TextRuleError("Conflicting increment for $conflictRange: $increment and ${other.increment}"))
                    }
                    val theseNonConflicting = (source rangeDifference conflictRange).map { Increment(it, increment, subRules) }
                    val conflicting = Increment(conflictRange, increment, subRules.combine(other.subRules))
                    val otherNonConflicting = (other.source rangeDifference conflictRange).map { Increment(it, increment, other.subRules) }
                    val sum = (theseNonConflicting + conflicting + otherNonConflicting).associateThrowOnConflict()
                    sum.singleOrNull()?.value ?: CompositeRule.FromExistingOnly(sum)
                }
            }

            is DeleteRule, is Update -> combineAsComposite(other)
        }
    }
}