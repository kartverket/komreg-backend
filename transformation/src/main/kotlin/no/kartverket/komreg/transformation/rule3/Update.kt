package no.kartverket.komreg.transformation.rule3

import arrow.core.fold
import arrow.core.raise.Raise
import no.kartverket.komreg.transformation.rule3.error.RuleError
import no.kartverket.komreg.transformation.rule3.error.TextRuleError
import no.kartverket.komreg.transformation.rule3.range.Range
import no.kartverket.komreg.transformation.rule3.util.combine
import org.pcollections.PMap

data class Update<A : Comparable<A>> internal constructor(
    override val source: Range.Point<A>,
    private val meta: PMap<String, Any?>,
    override val subRules: SubRuleMap
) : Rule.DirectFromExistingAndKeep<A>() {
    context (Raise<RuleError>)
    override fun combine(other: Rule.DirectFromExisting<A>): Rule.DirectOrCompositeFromExisting<A> {
        return when(other) {
            is DeleteRule -> combineAsComposite(other)
            is Increment-> other.combine(this@Update)
            is Update -> {
                if (this@Update == other) {
                    this@Update
                } else if (source == other.source) {
                    val newSubRuleMap = subRules.combine(other.subRules)
                    val newMeta = other.meta.fold(meta) { acc, (k, v) ->
                        if (acc.containsKey(k)) {
                            val otherV = acc[k]
                            if (otherV != v) {
                                raise(TextRuleError("Conflicting meta value for update of $source: $v and $otherV"))
                            }
                        }
                        acc.plus(k, v)
                    }
                    Update(source, newMeta, newSubRuleMap)
                } else {
                    combineAsComposite(other)
                }
            }
        }
    }
}