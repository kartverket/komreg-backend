@file:Suppress("NOTHING_TO_INLINE")

package no.kartverket.komreg.transformation.rule3.range

import arrow.core.raise.Raise
import arrow.core.raise.recover
import no.kartverket.komreg.transformation.rule3.util.And
import no.kartverket.komreg.transformation.rule3.util.Combinable
import no.kartverket.komreg.transformation.rule3.util.PartiallyCombinable
import no.kartverket.komreg.transformation.rule3.util.combine

@JvmInline
value class RangeVectorMap<AB, C : Comparable<C>, out V>(
    private val backingMap: RangeMap<C, MultiRangeMap<AB, V>>
) : MultiRangeMap<And<AB, C>, V> {

    override fun iterator(): Iterator<Map.Entry<RangeSet.Vector<AB, C>, V>> = iterator {
        for ((aes, bcs) in backingMap) {
            for (a in aes) {
                for ((bc, v) in bcs) {
                    yield(bc zip a mappedTo v)
                }
            }
        }
    }

    override fun get(key: And<AB, C>): V? {
        return backingMap[key.rightZipped]?.get(key.leftZipped)
    }

    override fun plus(
        ranges: RangeSet<And<AB, C>>,
        value: @UnsafeVariance V,
        valueMerge: (RangeSet<And<AB, C>>, V, V) -> @UnsafeVariance V
    ): RangeVectorMap<AB, C, V> {
                val abs = ranges.vector.leftRangeSet.allMappedTo(value)

        var newBackingMap = backingMap
        for (c in ranges.vector.rightRangeSet) {
            newBackingMap = newBackingMap.plus(c, abs) { cIntersect : Range<C>,  a, b ->
                b.fold(a) { acc, (ab, v) ->
                    acc.plus(ab, v) { abIntersect, yy, zz ->
                        valueMerge(abIntersect zip cIntersect, yy, zz)
                    }
                }
            }
        }

        return RangeVectorMap(newBackingMap)
    }

    override fun toMap(): Map<RangeSet.Vector<AB, C>, V> {
        val result = HashMap<RangeSet.Vector<AB, C>, V>()
        for ((cs, abs) in backingMap) {
            for (c in cs) {
                for ((ab, v) in abs) {
                    result[ab zip c] = v
                }
            }
        }
        return result
    }

    override fun getAll(keyRange: RangeSet<And<AB, C>>): Set<Map.Entry<RangeSet<And<AB, C>>, V>> {
        val result = HashSet<Map.Entry<RangeSet<And<AB, C>>, V>>()
        for (inputRange in keyRange.vector.rightRangeSet) {
            for (entry in backingMap.getAll(inputRange)) {
                val (outputRange, next) = entry
                for (cOutput in outputRange) {
                    for ((rangeSet, v) in next.getAll(keyRange.vector.leftRangeSet)) {
                        result.add(rangeSet zip cOutput mappedTo v)
                    }
                }
            }
        }
        return result
    }

    override fun <W> mapValues(transform: (Map.Entry<RangeSet<And<AB, C>>, V>) -> W): RangeVectorMap<AB, C, W> {
        return RangeVectorMap(backingMap.mapRangeValues { entry: Map.Entry<Range<C>, MultiRangeMap<AB, V>> ->
            entry.value.mapValues { (ab, v) ->
                transform(ab zip entry.key mappedTo v)
            }
        })
    }

    override fun map(
        valueMerge: (V, V) -> @UnsafeVariance V,
        transform: (Map.Entry<RangeSet<And<AB, C>>, V>) -> Map.Entry<RangeSet<And<AB, C>>, @UnsafeVariance V>
    ): RangeVectorMap<AB, C, V> {
        var resultBackingMap = emptyRangeMap<C, MultiRangeMap<AB, V>>()
        for (entry in backingMap) {
            val (cs, abs) = entry
            for (c in cs) {
                for ((ab, v) in abs) {
                    val (newRangeSet, newValue) = transform(ab zip c mappedTo v)
                    resultBackingMap = resultBackingMap.plus(c, newRangeSet.vector.leftRangeSet allMappedTo newValue) { _: Range<C>, a, b ->
                        b.fold(a) { acc, (ab, v) ->
                            acc.plus(ab, v) { _, v1, v2 ->
                                valueMerge(v1, v2)
                            }
                        }
                    }
                }
            }
        }
        return RangeVectorMap(resultBackingMap)
    }

    override fun <Error, R> Raise<Error>.fold(
        combineError: (Error, Error) -> Error,
        initial: R,
        operation: Raise<Error>.(acc: R, entry: Map.Entry<RangeSet<And<AB, C>>, V>) -> R
    ): R {
        var err: Error? = null
        var result = initial
        for (entry in this@RangeVectorMap) {
            result = recover({operation(result, entry)}) { e ->
                err = if (err == null) e else combineError(err!!, e)
                result
            }
        }
        return if (err != null) {
            raise(err!!)
        } else {
            result
        }
    }
}


/* Creates an empty rangevectormap */
fun <AB, C : Comparable<C>, V> emptyRangeVectorMap(): RangeVectorMap<AB, C, V> =
    RangeVectorMap(emptyRangeMap())

context (Raise<Error>)
inline operator fun <Error : Combinable<Error>, AB, C : Comparable<C>, V : PartiallyCombinable<Error, V>> RangeVectorMap<AB, C, V>.plus(newEntry: Map.Entry<RangeSet<And<AB, C>>, V>) : RangeVectorMap<AB, C, V> {
    return this.plus(newEntry.key, newEntry.value) { _, existingRule, additionalRule ->
        existingRule.combine(additionalRule)
    }
}

inline operator fun <AB, C : Comparable<C>, V : Combinable<V>> RangeVectorMap<AB, C, V>.plus(newEntry: Map.Entry<RangeSet<And<AB, C>>, V>) : RangeVectorMap<AB, C, V> {
    return this.plus(newEntry.key, newEntry.value) { _, existingRule, additionalRule ->
        existingRule.combine(additionalRule)
    }
}