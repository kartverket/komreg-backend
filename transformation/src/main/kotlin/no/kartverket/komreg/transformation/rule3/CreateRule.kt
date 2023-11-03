@file:Suppress("NOTHING_TO_INLINE")

package no.kartverket.komreg.transformation.rule3

import arrow.core.fold
import arrow.core.raise.Raise
import no.kartverket.komreg.core.util.kotlin.TypeTag
import no.kartverket.komreg.transformation.rule3.error.RuleError
import no.kartverket.komreg.transformation.rule3.error.TextRuleError
import no.kartverket.komreg.transformation.rule3.range.*
import no.kartverket.komreg.transformation.rule3.util.PartiallyCombinable
import no.kartverket.komreg.transformation.rule3.util.combine
import org.pcollections.*
import java.lang.IllegalStateException

data class CreateRule<A : Comparable<A>>(
    val target: Range.Point<A>,
    val meta: Map<String, Any?>
    ) : Rule.Direct<A>(), Rule.DirectOrCompositeCreate<A> {

    @JvmInline
    value class SubRuleMap internal constructor(
        private val backingMap: PMap<TypeTag<*>, Rule.DirectOrCompositeCreate<*>>
    ) : Map<TypeTag<*>, Rule.DirectOrCompositeCreate<*>> by backingMap, PartiallyCombinable<RuleError, SubRuleMap> {
        data class Entry<A : Comparable<A>>(
            override val key: TypeTag<A>,
            override val value: Rule.DirectOrCompositeCreate<A>
        ) : Map.Entry<TypeTag<A>, Rule.DirectOrCompositeCreate<A>>

        companion object {
            operator fun Raise<RuleError>.invoke(entries: Iterable<Entry<*>>): SubRuleMap {
                val newBackingMap = entries
                    .fold<Entry<*>, PMap<TypeTag<*>, Rule.DirectOrCompositeCreate<Comparable<Any>>>>(HashPMap.empty(IntTreePMap.empty())) { acc, (key, value) ->
                        @Suppress("UNCHECKED_CAST")
                        val mergedValue = when(val existing = acc[key]) {
                            is Rule.DirectOrComposite<*> -> existing.combine(value as Rule.DirectOrCompositeCreate<Comparable<Any>>)
                            null -> value as Rule.DirectOrCompositeCreate<Comparable<Any>>
                        }

                        if (mergedValue !is Rule.DirectOrCompositeCreate) {
                            raise(TextRuleError("Invalid sub rules for create rule: $mergedValue"))
                        }

                        acc.plus(key, mergedValue)

                    }
                @Suppress("UNCHECKED_CAST") // PMap er covariant for V, så dette er OK
                return SubRuleMap(newBackingMap as PMap<TypeTag<*>, Rule.DirectOrCompositeCreate<*>>)
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun <A : Comparable<A>> getTyped(key: TypeTag<A>): Rule.DirectOrComposite<A>? {
            return backingMap[key] as? Rule.DirectOrComposite<A>
        }

        override val entries: Set<Entry<*>>
            get() = backingMap.entries.mapTo(HashSet()) { entry ->
                @Suppress("UNCHECKED_CAST")
                Entry(
                    entry.key as TypeTag<Comparable<Any>>,
                    entry.value as CreateRule<Comparable<Any>>
                )
            }


        context (Raise<RuleError>)
        private inline operator fun <A : Comparable<A>> PMap<TypeTag<*>, Rule.DirectOrCompositeCreate<*>>.plus(
            entry: Map.Entry<TypeTag<A>, Rule.DirectOrCompositeCreate<A>>
        ) : PMap<TypeTag<*>, Rule.DirectOrCompositeCreate<*>> {
            val (k, v) = entry
            val mergedValue = getTyped(k)?.combine(v) ?: v

            if (mergedValue !is Rule.DirectOrCompositeCreate) {
                raise(TextRuleError("A ${mergedValue::class.simpleName} can not be a sub rule: $mergedValue"))
            }

            return plus(k, mergedValue)
        }

        override fun Raise<RuleError>.combine(other: SubRuleMap): SubRuleMap {
            return SubRuleMap(other.backingMap.fold(backingMap) { acc, entry: Map.Entry<TypeTag<*>, Rule.DirectOrCompositeCreate<*>> ->
                @Suppress("UNCHECKED_CAST")
                acc + entry as Map.Entry<TypeTag<Comparable<Any>>, Rule.DirectOrCompositeCreate<Comparable<Any>>>
            })
        }
    }

    override fun Raise<RuleError>.combine(other: Rule.Direct<A>): Rule.DirectOrComposite<A> {
        return when(other) {
            is CreateRule -> return combine(other)
            is Rule.DirectFromExisting -> {
                CompositeRule.FromExistingOrCreate(
                    rangeMapOf(this@CreateRule.target mappedTo this@CreateRule),
                    other.source.allMappedTo(other)
                )
            }

        }
    }

    fun Raise<RuleError>.combine(other: CreateRule<A>): Rule.DirectOrCompositeCreate<A> {
        return if (target != other.target) {
            val createRules = rangeMapOf(
                this@CreateRule.target mappedTo this@CreateRule,
                other.target mappedTo other
            ) { _, _, _ -> throw IllegalStateException("Should not be possible") }
            CompositeRule.CreateOnly(
                createRules
            )
        } else {
            val newMeta = other.meta.entries.fold(TreePMap.from(meta)) { acc, (k, v) ->
                acc[k].let { if (it != v) raise(TextRuleError("Incompatible values for create rules of $target: $it != $v")) }
                acc.plus(k, v)
            }
            CreateRule(target, newMeta)
        }
    }

}

fun <A : Comparable<A>> CreateRule<A>.associate() : RangeMap<A, CreateRule<A>> {
    return rangeMapOf(this.target mappedTo this)
}

context (Raise<RuleError>)
inline fun <A : Comparable<A>> CreateRule<A>.plus(other: CreateRule<A>) : Rule.DirectOrCompositeCreate<A> = this@Raise.combine(other)

