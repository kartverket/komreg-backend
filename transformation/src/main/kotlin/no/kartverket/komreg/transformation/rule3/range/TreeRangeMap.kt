package no.kartverket.komreg.transformation.rule3.range

import arrow.core.*
import arrow.core.raise.Raise
import arrow.core.raise.recover
import no.kartverket.komreg.transformation.rule3.util.Combinable
import no.kartverket.komreg.transformation.rule3.util.PartiallyCombinable
import no.kartverket.komreg.transformation.rule3.util.combine
import org.pcollections.PSortedMap
import org.pcollections.TreePMap

@JvmInline
value class TreeRangeMap<K : Comparable<K>, out V> private constructor(
    private val backingMap: PSortedMap<Range<K>, V>
) : RangeMap<K, V> {

    internal class RangeStartComparator<K : Comparable<K>> : Comparator<Range<K>> {
        override fun compare(firstRange: Range<K>, secondRange: Range<K>): Int {
            return firstRange.startEndpoint.compareTo(secondRange.startEndpoint)
        }
    }

    companion object {
        @JvmStatic
        @JvmName("empty")
        operator fun <K : Comparable<K>, V> invoke(): TreeRangeMap<K, V> {
            return TreeRangeMap(TreePMap.empty(RangeStartComparator()))
        }

        @JvmStatic
        @JvmName("ofPairs")
        operator fun <K : Comparable<K>, V> invoke(
            entries: Iterable<Pair<Range<K>, V>>,
            valueMerge: (V, V) -> V
        ): TreeRangeMap<K, V> {
            val backingMap = entries
                .fold<Pair<Range<K>, V>, PSortedMap<Range<K>, V>>(
                    TreePMap.empty(RangeStartComparator())
                ) { backingMap, (range, value) ->
                    backingMap.plus(range, value) { _, a, b ->
                        valueMerge(a, b)
                    }
                }

            return TreeRangeMap(backingMap)
        }

        internal fun <K : Comparable<K>, V> makeOrThrow(backingMap: TreePMap<Range<K>, V>): TreeRangeMap<K, V> {
            require(backingMap.comparator() is RangeStartComparator<*>) {
                "Backing map must have a RangeStartComparator"
            }
            backingMap.forEach { (range, _) ->
                backingMap.lowerKey(range)?.let { lowerRange ->
                    require(lowerRange.endEndpoint <= range.startEndpoint) {
                        "Ranges must not overlap"
                    }
                }
            }
            return TreeRangeMap<K, V>(backingMap)
        }

        @JvmStatic
        @JvmName("ofEntries")
        operator fun <K : Comparable<K>, V> invoke(
            entries: Iterable<Map.Entry<Range<K>, V>>,
            valueMerge: (conflictRange: Range<K>, existingValue: V, additionalValue: V) -> V
        ): TreeRangeMap<K, V> {
            val backingMap = entries
                .fold<Map.Entry<Range<K>, V>, PSortedMap<Range<K>, V>>(
                    TreePMap.empty(RangeStartComparator())
                ) { backingMap, (range, value) ->
                    backingMap.plus(range, value) { conflictRange, a, b ->
                        valueMerge(conflictRange, a, b)
                    }
                }

            return TreeRangeMap(backingMap)
        }

        private fun <K : Comparable<K>, V> PSortedMap<Range<K>, V>.plus(
            range: Range<K>,
            value: V,
            mergeValue: (Range<K>, V, V) -> V
        ): PSortedMap<Range<K>, V> {
            if (isEmpty()) {
                return plus(range, value)
            }

            var result: PSortedMap<Range<K>, V> = this
            result = firstKey()
                ?.takeIf { it.startEndpoint > range.startEndpoint }
                ?.let { firstKey ->
                    result.plus(Range(range.startEndpoint, minOf(firstKey.startEndpoint, range.endEndpoint)), value)
                }
                ?: result
            result = lastKey()
                ?.takeIf { it.startEndpoint > range.startEndpoint && it.endEndpoint < range.endEndpoint }
                ?.let { lastKey ->
                    result.plus(Range(lastKey.endEndpoint, range.endEndpoint), value)
                }
                ?: result
            result = result.cut(range.startEndpoint).cut(range.endEndpoint)

            val lastEntryInRange = (atLeast(range.endEndpoint)?.let(result::lowerEntry) ?: result.lastEntry())
                ?.takeIf { (floorRange, _) -> floorRange.startEndpoint >= range.startEndpoint && floorRange.endEndpoint <= range.endEndpoint }
                ?.let { firstFloorEntry ->
                    var lastFloorEntry: Map.Entry<Range<K>, V> = firstFloorEntry
                    var peekFloorEntry: Map.Entry<Range<K>, V>? = firstFloorEntry
                    while (peekFloorEntry != null && peekFloorEntry.key.endEndpoint <= range.endEndpoint) {
                        lastFloorEntry = peekFloorEntry
                        peekFloorEntry = result.higherEntry(lastFloorEntry.key)
                    }
                    lastFloorEntry
                }

            if (lastEntryInRange != null) {
                var entryInRange: Map.Entry<Range<K>, V> = lastEntryInRange
                if (lastEntryInRange.key.endEndpoint < range.endEndpoint) {
                    result = result.plus(Range(lastEntryInRange.key.endEndpoint, range.endEndpoint), value)
                }
                while (entryInRange.key.endEndpoint > range.startEndpoint) {
                    val peekEntry = result
                        .lowerEntry(entryInRange.key)
                        ?.takeIf { (peekRange, _) -> peekRange.startEndpoint >= range.startEndpoint }
                    val mergedValue = mergeValue(
                        peekEntry?.let { (peekRange, _) ->
                            Range(
                                peekRange.startEndpoint,
                                entryInRange.key.endEndpoint
                            )
                        } ?: entryInRange.key,
                        entryInRange.value,
                        value
                    )
                    result = result.plus(entryInRange.key, mergedValue)
                    if (peekEntry != null) {
                        if (peekEntry.key.endEndpoint < entryInRange.key.startEndpoint) {
                            result =
                                result.plus(Range(peekEntry.key.endEndpoint, entryInRange.key.startEndpoint), value)
                        }
                        entryInRange = peekEntry
                    } else {
                        if (entryInRange.key.startEndpoint > range.startEndpoint) {
                            result = result.plus(Range(range.startEndpoint, entryInRange.key.startEndpoint), value)
                        }
                        break
                    }
                }
            } else {
                result = result.plus(range, value)
            }

            return result.mergeConsecutive()
        }

        private fun <K : Comparable<K>, V> PSortedMap<Range<K>, V>.mergeConsecutive(): PSortedMap<Range<K>, V> {
            var entry: Map.Entry<Range<K>, V>? = firstEntry()
            var result = this
            while (entry != null) {
                val nextEntry = atLeast(entry.key.endEndpoint)?.let(result::ceilingEntry)
                entry =
                    if (nextEntry != null && entry.key.endEndpoint == nextEntry.key.startEndpoint && entry.value == nextEntry.value) {
                        val newRange = Range(entry.key.startEndpoint, nextEntry.key.endEndpoint)
                        result = result.minus(entry.key).minus(nextEntry.key).plus(newRange, entry.value)
                        newRange mappedTo entry.value
                    } else {
                        nextEntry
                    }
            }
            return result
        }

        private fun <K : Comparable<K>, V> PSortedMap<Range<K>, V>.cut(endpoint: Endpoint<K>): PSortedMap<Range<K>, V> {
            val floorEntry =
                Range(endpoint, Endpoint.PositiveInfinity())?.let(::floorEntry)
            return if (floorEntry != null && floorEntry.key.endEndpoint > endpoint) {
                val (start, end) = floorEntry.key
                minus(floorEntry.key as Any)
                    .let {
                        if (start < endpoint) {
                            it.plus(requireNotNull(Range(start, endpoint)), floorEntry.value)
                        } else {
                            it
                        }
                    }
                    .let {
                        if (endpoint < end) {
                            it.plus(requireNotNull(Range(endpoint, end)), floorEntry.value)
                        } else {
                            it
                        }
                    }
            } else {
                return this
            }
        }

        private fun <K : Comparable<K>, V> PSortedMap<Range<K>, V>.minusRange(range: Range<K>): PSortedMap<Range<K>, V> {
            var cut = cut(range.startEndpoint).cut(range.endEndpoint)
            var entry: Map.Entry<Range<K>, V>? = cut.floorEntry(range)
            while (entry != null && entry.key.endEndpoint <= range.endEndpoint) {
                cut = cut.minus(entry.key)
                entry = cut.higherEntry(entry.key)
            }
            return cut
        }
    }

    override fun get(key: K): V? {
        return backingMap
            .floorEntry(Range.LowerBounded(key))
            ?.takeIf { (range, _) -> range.contains(key) }
            ?.let { (_, value) -> value }
    }

    override fun getAll(keyRange: RangeSet<K>): Set<Map.Entry<Range<K>, V>> {
        val result = HashSet<Map.Entry<Range<K>, V>>()
        for (kRange in keyRange) {
            backingMap.floorEntry(kRange).takeIf { (range, _) -> range.endEndpoint > kRange.startEndpoint }
                ?.let(result::add)
            backingMap.higherEntry(kRange).takeIf { (range, _) -> kRange.contains(range.startEndpoint) }
                ?.let(result::add)
        }
        return result
    }

    override fun plus(
        range: Range<K>,
        value: @UnsafeVariance V,
        valueMerge: (Range<K>, V, V) -> @UnsafeVariance V
    ): RangeMap<K, V> {
        return TreeRangeMap(backingMap.plus(range, value, valueMerge))
    }

    override fun minus(range: Range<K>): RangeMap<K, V> {
        return TreeRangeMap(backingMap.minusRange(range))
    }

    override val values: Collection<V>
        get() = backingMap.values

    override fun toMap(): Map<Range<K>, V> {
        return backingMap
    }

    override fun <W> mapRangeValues(transform: (Map.Entry<Range<K>, V>) -> W): RangeMap<K, W> {
        val backingMap = TreePMap
            .empty<Range<K>, W>(RangeStartComparator())
            .plusAll(backingMap.mapValues(transform))
        return TreeRangeMap(backingMap)
    }

    override fun map(
        valueMerge: (V, V) -> @UnsafeVariance V,
        transform: (Map.Entry<RangeSet<K>, V>) -> Map.Entry<RangeSet<K>, @UnsafeVariance V>
    ): TreeRangeMap<K, V> {
        var resultBackingMap: PSortedMap<Range<K>, V> = TreePMap.empty(RangeStartComparator())

        for (entry in backingMap.entries) {
            val (range, value) = entry
            val (newRangeSet, newValue) = transform(range mappedTo value)
            for (newRange in newRangeSet) {
                resultBackingMap = resultBackingMap.plus(newRange, newValue) { _, a, b ->
                    valueMerge(a, b)
                }
            }
        }

        return TreeRangeMap(resultBackingMap)
    }

    override fun iterator(): Iterator<Map.Entry<Range<K>, V>> {
        return backingMap.iterator()
    }

    override fun <Error, R> Raise<Error>.fold(
        combineError: (Error, Error) -> Error,
        initial: R,
        operation: Raise<Error>.(acc: R, entry: Map.Entry<RangeSet<K>, V>) -> R
    ): R {
        var err: Error? = null

        val result = backingMap.fold(initial) { acc, (range, value) ->
            recover({ operation(acc, range mappedTo value) }) { e ->
                err = if (err == null) {
                    e
                } else {
                    combineError(err!!, e)
                }
            }
            acc
        }

        return if (err == null) {
            result
        } else {
            raise(err!!)
        }
    }
}

fun <K : Comparable<K>, V> emptyRangeMap(): RangeMap<K, V> {
    return TreeRangeMap()
}

fun <K : Comparable<K>, V> rangeMapOf(
    entry: Map.Entry<Range<K>, V>
): RangeMap<K, V> {
    return rangeMapOf(*arrayOf(entry)).getOrElse { error(it) }
}

fun <K : Comparable<K>, V> rangeMapOf(
    vararg entries: Map.Entry<Range<K>, V>,
    mergeValue: (conflictRange: Range<K>, existingValue: V, additionalValue: V) -> V
): RangeMap<K, V> {
    return TreeRangeMap.invoke(entries.asIterable(), mergeValue)
}

fun <K : Comparable<K>, V> rangeMapOf(
    vararg entries: Map.Entry<Range<K>, V>
): Either<String, RangeMap<K, V>> {
    return try {
        TreeRangeMap(entries.asIterable()) { r, a, b ->
            if (a != b) throw IllegalArgumentException("Merge not specified, can not merge $a and $b for $r")
            else {
                a
            }
        }.right()
    } catch (e: IllegalArgumentException) {
        e.message!!.left()
    }
}

@JvmName("rangeMapOfSet")
fun <K : Comparable<K>, V> rangeMapOf(
    vararg entries: Map.Entry<Range<K>, Set<V>>
): RangeMap<K, Set<V>> {
    return TreeRangeMap(entries.asIterable()) { _, a, b ->
        a + b
    }
}

@JvmName("rangeMapOfCombinable")
fun <K : Comparable<K>, V : Combinable<V>> rangeMapOf(
    vararg entries: Map.Entry<Range<K>, V>
): RangeMap<K, V> {
    return TreeRangeMap(entries.asIterable()) { _, a, b ->
        a.combine(b)
    }
}

context (Raise<Error>)
@JvmName("rangeMapOfPartiallyCombinable")
fun <Error : Combinable<Error>, K : Comparable<K>, V : PartiallyCombinable<Error, V>> rangeMapOf(
    firstEntry: Map.Entry<Range<K>, V>,
    secondEntry: Map.Entry<Range<K>, V>,
    vararg moreEntries: Map.Entry<Range<K>, V>
): RangeMap<K, V> {
    val entries = listOf(firstEntry, secondEntry, *moreEntries)
    var errs: Error? = null
    val result = TreeRangeMap(entries.asIterable()) { _, a, b ->
        recover({ a.combine(b) }) { err ->
            errs = if (errs != null) {
                errs!!.combine(err)
            } else {
                err
            }
            a
        }
    }
    if (errs != null) {
        raise(errs!!)
    }
    return result
}

@JvmName("rangeMapOfBoolean")
fun <K : Comparable<K>, V> rangeMapOf(
    vararg entries: Map.Entry<Range<K>, Boolean>
): RangeMap<K, Boolean> {
    return TreeRangeMap.invoke(entries.asIterable()) { _, a, b ->
        a || b
    }
}



