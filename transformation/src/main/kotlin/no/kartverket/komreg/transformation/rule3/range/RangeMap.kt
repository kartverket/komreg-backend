package no.kartverket.komreg.transformation.rule3.range

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.Raise
import no.kartverket.komreg.transformation.rule3.util.Combinable
import no.kartverket.komreg.transformation.rule3.util.PartiallyCombinable
import no.kartverket.komreg.transformation.rule3.util.combine

interface RangeMap<K : Comparable<K>, out V> : MultiRangeMap<K, V> {

    val values: Collection<V>

    fun plus(
        range: Range<K>,
        value: @UnsafeVariance V,
        valueMerge: (conflictRange: Range<K>, existingValue: V, additionalValue: V) -> @UnsafeVariance V
    ): RangeMap<K, V>

    fun minus(range: Range<K>): RangeMap<K, V>

    fun <W> mapRangeValues(transform: (Map.Entry<Range<K>, V>) -> W): RangeMap<K, W>

    override fun <W> mapValues(transform: (Map.Entry<RangeSet<K>, V>) -> W): RangeMap<K, W> {
        return mapRangeValues { entry: Map.Entry<Range<K>, V> -> transform(entry) }
    }

    override fun map(
        valueMerge: (V, V) -> @UnsafeVariance V,
        transform: (Map.Entry<RangeSet<K>, V>) -> Map.Entry<RangeSet<K>, @UnsafeVariance V>
    ): RangeMap<K, V>

    fun minus(ranges: RangeSet<K>): RangeMap<K, V> {
        var result = this
        for (range in ranges.scalar) {
            result = result.minus(range)
        }
        return result
    }

    override fun plus(ranges: RangeSet<K>, value: @UnsafeVariance V, valueMerge: (RangeSet<K>, V, V) -> @UnsafeVariance V): RangeMap<K, V> {
        var result = this
        for (range in ranges.scalar) {
            result = result.plus(range, value) { intersectRange: Range<K>, self, other ->
                valueMerge(intersectRange, self, other)
            }
        }
        return result
    }
}


fun <K : Comparable<K>, V, Error> RangeMap<K, Either<Error, V>>.toEither(combineError: (Error, Error) -> Error): Either<Error, RangeMap<K, V>> {
    var err : Error? = null

    val result = mapValues { (_, v) ->
        when (v) {
            is Either.Left -> {
                err = if (err == null) v.value else combineError(err!!, v.value)
                null
            }
            is Either.Right -> v.value
        }
    }

    if (err != null) {
        return err!!.left()
    } else {
        @Suppress("UNCHECKED_CAST")
        return result as Either<Error, RangeMap<K, V>>
    }
}



@JvmName("plusIterable")
operator fun <K : Comparable<K>, V> RangeMap<K, Iterable<V>>.plus(pair: Map.Entry<Range<K>, Iterable<V>>): RangeMap<K, Iterable<V>> {
    return plus(pair.key, pair.value) { _: Range<K>, a, b -> a + b }
}

@JvmName("plusCollection")
operator fun <K : Comparable<K>, V> RangeMap<K, Collection<V>>.plus(pair: Map.Entry<Range<K>, Collection<V>>): RangeMap<K, Collection<V>> {
    return plus(pair.key, pair.value) { _ : Range<K>, a, b -> a + b }
}

@JvmName("plusList")
operator fun <K : Comparable<K>, V> RangeMap<K, List<V>>.plus(pair: Map.Entry<Range<K>, List<V>>): RangeMap<K, List<V>> {
    return plus(pair.key, pair.value) { _ : Range<K>, a, b -> a + b }
}

@JvmName("plusSet")
operator fun <K : Comparable<K>, V> RangeMap<K, Set<V>>.plus(pair: Map.Entry<Range<K>, Set<V>>): RangeMap<K, Set<V>> {
    return plus(pair.key, pair.value) { _ : Range<K>, a, b -> a + b }
}

@JvmName("plusSetElement")
operator fun <K : Comparable<K>, V> RangeMap<K, Set<V>>.plus(pair: Map.Entry<Range<K>, V>): RangeMap<K, Set<V>> {
    return plus(pair.key, setOf(pair.value)) { _ : Range<K>, a, b -> a + b }
}

context (Raise<Error>)
inline operator fun <Error : Combinable<Error>, K : Comparable<K>, V : PartiallyCombinable<Error, V>> RangeMap<K, V>.plus(newEntry: Map.Entry<RangeSet<K>, V>) : RangeMap<K, V> {
    return plus(newEntry.key, newEntry.value) { _, existingValue, additionalValue ->
        existingValue.combine(additionalValue)
    }
}

//
//operator fun <K : Comparable<K>, V> RangeMap<K, V>.plus(pair: Map.Entry<Range<K>, V>): RangeMap<K, Set<V>> {
//    TODO()
//}