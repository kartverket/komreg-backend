@file:Suppress("NOTHING_TO_INLINE")

package no.kartverket.komreg.transformation.rule3.range

import arrow.core.raise.Raise
import no.kartverket.komreg.transformation.rule3.util.Combinable
import no.kartverket.komreg.transformation.rule3.util.PartiallyCombinable
import no.kartverket.komreg.transformation.rule3.util.combine
import java.util.AbstractMap

sealed interface MultiRangeMap<K, out V> : Iterable<Map.Entry<RangeSet<K>, V>> {

    fun containsKey(key: K): Boolean = get(key) != null
    operator fun get(key: K): V?
    fun plus(
        ranges: RangeSet<K>,
        value: @UnsafeVariance V,
        valueMerge: (RangeSet<K>, V, V) -> @UnsafeVariance V
    ): MultiRangeMap<K, V>

    fun toMap(): Map<out RangeSet<K>, V>
    fun map(
        valueMerge: (V, V) -> @UnsafeVariance V,
        transform: (Map.Entry<RangeSet<K>, V>) -> Map.Entry<RangeSet<K>, @UnsafeVariance V>
    ): MultiRangeMap<K, V>

    fun <W> mapValues(transform: (Map.Entry<RangeSet<K>, V>) -> W): MultiRangeMap<K, W>
    fun getAll(keyRange: RangeSet<K>): Set<Map.Entry<RangeSet<K>, V>>


    fun <Error, R> Raise<Error>.fold(
        combineError: (Error, Error) -> Error,
        initial: R,
        operation: Raise<Error>.(acc: R, entry: Map.Entry<RangeSet<K>, V>) -> R
    ): R

}

context (Raise<Error>)
inline fun <K, V, Error, R> MultiRangeMap<K, V>.fold(
    noinline combineError: (Error, Error) -> Error,
    initial: R,
    noinline operation: Raise<Error>.(acc: R, entry: Map.Entry<RangeSet<K>, V>) -> R
): R {
    return this@Raise.fold(combineError, initial, operation)
}

context (Raise<Error>)
inline fun <K, V, Error : Combinable<Error>, R> MultiRangeMap<K, V>.fold(
    initial: R,
    noinline operation: Raise<Error>.(acc: R, entry: Map.Entry<RangeSet<K>, V>) -> R
): R {
    return this@Raise.fold({e1, e2 -> e1.combine(e2) }, initial, operation)
}


context (Raise<Error>)
inline operator fun <Error : Combinable<Error>, K, V : PartiallyCombinable<Error, V>> MultiRangeMap<K, V>.plus(newEntry: Map.Entry<RangeSet<K>, V>) : MultiRangeMap<K, V> {
    return plus(newEntry.key, newEntry.value) { _, existingValue, additionalValue ->
        existingValue.combine(additionalValue)
    }
}


inline operator fun <K, V : Combinable<V>> MultiRangeMap<K, V>.plus(newEntry: Map.Entry<RangeSet<K>, V>) : MultiRangeMap<K, V> {
    return plus(newEntry.key, newEntry.value) { _, existingValue, additionalValue ->
        existingValue.combine(additionalValue)
    }
}


//TODO: Flytt til et mer passende sted
infix fun <K, V> K.mappedTo(value: V): Map.Entry<K, V> = AbstractMap.SimpleImmutableEntry(this, value)

