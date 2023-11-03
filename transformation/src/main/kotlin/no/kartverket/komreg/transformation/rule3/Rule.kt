@file:Suppress("NOTHING_TO_INLINE")

package no.kartverket.komreg.transformation.rule3

import arrow.core.fold
import arrow.core.raise.Raise
import no.kartverket.komreg.core.util.kotlin.TypeTag
import no.kartverket.komreg.transformation.rule3.error.RuleError
import no.kartverket.komreg.transformation.rule3.error.TextRuleError
import no.kartverket.komreg.transformation.rule3.range.*
import no.kartverket.komreg.transformation.rule3.util.*
import org.pcollections.*
import java.lang.IllegalArgumentException

sealed interface Rule<RuleA : Rule<RuleA, A>, A> : PartiallyCombinable<RuleError, RuleA> {

    sealed interface DirectOrComposite<A : Comparable<A>> : Rule<DirectOrComposite<A>, A>
    sealed interface DirectOrCompositeCreate<A : Comparable<A>> : DirectOrComposite<A>
    sealed interface DirectOrCompositeFromExisting<A : Comparable<A>> : DirectOrComposite<A>

    sealed class Direct<A : Comparable<A>> : DirectOrComposite<A> {
        final override fun Raise<RuleError>.combine(other: DirectOrComposite<A>): DirectOrComposite<A> {
            return when (other) {
                is CompositeRule<A> -> other.combine(this@Direct)
                is Direct<A> -> combine(other)
            }
        }

        abstract fun Raise<RuleError>.combine(other: Direct<A>): DirectOrComposite<A>
    }

    sealed class DirectFromExisting<A : Comparable<A>> : Direct<A>(), DirectOrCompositeFromExisting<A> {
        abstract val source: Range<A>
        final override fun Raise<RuleError>.combine(other: Direct<A>): DirectOrComposite<A> {
            return when (other) {
                is CreateRule -> CompositeRule.FromExistingOrCreate(other.associate(), associate())
                is DirectFromExisting -> combine(other)
            }
        }

        context (Raise<RuleError>)
        abstract fun combine(other: DirectFromExisting<A>): DirectOrCompositeFromExisting<A>

        context (Raise<RuleError>)
        fun combineAsComposite(other: DirectFromExisting<A>) : CompositeRule.FromExistingOnly<A> {
            return CompositeRule.FromExistingOnly(associate().plus(other.source, other) { cr: Range<A>, a, b ->
                raise(TextRuleError("Conflicting rules for $cr:\n\t$a\n\t$b"))
            })
        }

    }

    sealed class DirectFromExistingAndKeep<A : Comparable<A>> : DirectFromExisting<A>() {
        @JvmInline
        value class SubRuleMap internal constructor(
            private val backingMap: PMap<TypeTag<*>, DirectOrComposite<*>>
        ) : Map<TypeTag<*>, DirectOrComposite<*>> by backingMap, PartiallyCombinable<RuleError, SubRuleMap> {
            data class Entry<A : Comparable<A>>(
                override val key: TypeTag<A>,
                override val value: DirectOrComposite<A>
            ) : Map.Entry<TypeTag<A>, DirectOrComposite<A>>

            companion object {
                @Suppress("UNCHECKED_CAST")
                operator fun Raise<RuleError>.invoke(entries: Iterable<Entry<*>>): SubRuleMap {
                    val newBackingMap = entries
                        .fold<Entry<*>, PMap<TypeTag<*>, DirectOrComposite<Comparable<Any>>>>(
                            HashPMap.empty(
                                IntTreePMap.empty()
                            )
                        ) { acc, (key, value) ->
                            val existingRule = acc[key]
                            if (existingRule != null) {
                                acc.plus(
                                    key,
                                    existingRule.combineAsCreateRules(value as DirectOrComposite<Comparable<Any>>)
                                )
                            } else {
                                acc.plus(key, value as DirectOrComposite<Comparable<Any>>)
                            }
                        }
                    return SubRuleMap(newBackingMap as PMap<TypeTag<*>, DirectOrComposite<*>>)
                }
            }



            @Suppress("UNCHECKED_CAST")
            fun <A : Comparable<A>> getTyped(key: TypeTag<A>): DirectOrComposite<A>? {
                return backingMap[key] as? DirectOrComposite<A>
            }

            override val entries: Set<Entry<*>>
                get() = backingMap.entries.mapTo(HashSet()) { entry ->
                    @Suppress("UNCHECKED_CAST")
                    Entry(
                        entry.key as TypeTag<Comparable<Any>>,
                        entry.value as DirectOrComposite<Comparable<Any>>
                    )
                }


            context (Raise<RuleError>)
            private inline operator fun <A : Comparable<A>> PMap<TypeTag<*>, DirectOrComposite<*>>.plus(
                entry: Map.Entry<TypeTag<A>, DirectOrComposite<A>>
            ) : PMap<TypeTag<*>, DirectOrComposite<*>> {
                val (k, v) = entry
                return plus(k, getTyped(k)?.combine(v) ?: v)
            }

            override fun Raise<RuleError>.combine(other: SubRuleMap): SubRuleMap {
                return SubRuleMap(other.backingMap.fold(backingMap) { acc, entry ->
                    @Suppress("UNCHECKED_CAST")
                    acc + entry as Map.Entry<TypeTag<Comparable<Any>>, DirectOrComposite<Comparable<Any>>>
                })
            }
        }

        companion object {
            fun emptySubRuleMap() : SubRuleMap {
                return SubRuleMap(HashPMap.empty(IntTreePMap.empty()))
            }
        }


        abstract val subRules: SubRuleMap
    }

    sealed interface Prefixed<A, B : Comparable<B>> : Rule<Prefixed<A, B>, And<A, B>> {
        abstract val backingMap: MultiRangeMap<A, DirectOrComposite<B>>
    }

    @JvmInline
    value class PrefixedByOne<A : Comparable<A>, B : Comparable<B>>(
        override val backingMap: RangeMap<A, DirectOrComposite<B>>
    )  : Prefixed<A, B> {

        override fun Raise<RuleError>.combine(other: Prefixed<A, B>): Prefixed<A, B> {
            other as PrefixedByOne
            val newBackingMap = other
                .backingMap
                .fold(backingMap) { acc, entry ->
                    acc + entry
                }
            return PrefixedByOne(newBackingMap)
        }
    }

    @JvmInline
    value class PrefixedByMore<A, B : Comparable<B>>(
        override val backingMap: MultiRangeMap<A, DirectOrComposite<B>>
    )  : Prefixed<A, B> {

        override fun Raise<RuleError>.combine(other: Prefixed<A, B>): Prefixed<A, B> {
            other as PrefixedByMore
            val newBackingMap = other
                .backingMap
                .fold(backingMap) { acc, entry ->
                    acc + entry
                }
            return PrefixedByMore(newBackingMap)
        }
    }

}

fun <K : Comparable<K>, V : Rule.DirectFromExisting<K>> V.associate(): RangeMap<K, V> {
    return rangeMapOf(this.source mappedTo this)
}

fun <K : Comparable<K>, V : Rule.DirectFromExisting<K>> Iterable<V>.associate(
    valueMerge: (conflictRange: Range<K>, existingValue: V, additionalValue: V) -> V
) : RangeMap<K, V> {
    return this.fold(emptyRangeMap()) { acc, x ->
        acc.plus(x.source, x, valueMerge)
    }
}
fun <K : Comparable<K>, V : Rule.DirectFromExisting<K>> Iterable<V>.associateThrowOnConflict() : RangeMap<K, V> {
    return this.associate { conflictRange, existingValue, additionalValue ->
        throw IllegalArgumentException("Conflicting rules for $conflictRange:\n\t$existingValue\n\t$additionalValue")
    }
}
