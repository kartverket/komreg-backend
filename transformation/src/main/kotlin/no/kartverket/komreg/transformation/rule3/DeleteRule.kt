package no.kartverket.komreg.transformation.rule3

import arrow.core.raise.Raise
import no.kartverket.komreg.transformation.rule3.error.RuleError
import no.kartverket.komreg.transformation.rule3.error.TextRuleError
import no.kartverket.komreg.transformation.rule3.range.*
import no.kartverket.komreg.transformation.rule3.util.combine

data class DeleteRule<A : Comparable<A>>(
    override val source: Range<A>
) : Rule.DirectFromExisting<A>() {
    context (Raise<RuleError>)
    override fun combine(other: Rule.DirectFromExisting<A>): Rule.DirectOrCompositeFromExisting<A> {
        return when (other) {
            is DeleteRule -> when (val sourceUnion = source rangeUnion other.source) {
                is Range -> DeleteRule(sourceUnion)
                else -> CompositeRule.FromExistingOnly(sourceUnion
                    .map { DeleteRule(it) }
                    .associateThrowOnConflict())
            }
            is Rule.DirectFromExistingAndKeep -> combineAsComposite(other)
        }
    }
}