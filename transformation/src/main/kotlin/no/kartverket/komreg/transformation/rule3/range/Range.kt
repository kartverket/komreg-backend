package no.kartverket.komreg.transformation.rule3.range

sealed interface Range<A : Comparable<A>> : RangeSet.Scalar<A> {

    class Unbounded<A : Comparable<A>> : Range<A> {
        override val startValue: A? get() = null
        override val endExclusiveValue: A? get() = null

        override fun contains(value: A): Boolean {
            return true
        }

        override fun toIterableDomain(): Unbounded<A> {
            return this
        }

        override fun toSet(): Set<Unbounded<A>> {
            return setOf(toIterableDomain())
        }

        override fun toString(): String = "(-∞..+∞)"

        override fun equals(other: Any?): Boolean {
            return other is Unbounded<*>
        }

        override fun hashCode(): Int {
            return 0
        }
    }

    sealed interface PartiallyBounded<A : Comparable<A>> : Range<A>

    sealed interface LowerBounded<A : Comparable<A>> : PartiallyBounded<A> {
        override val startValue: A
        override fun toSet(): Set<LowerBounded<A>>

        companion object {
            operator fun <A : Comparable<A>> invoke(start: A): LowerBounded<A> {
                return LowerBoundedImpl(start)
            }
        }
    }

    sealed class Bounded<A : Comparable<A>> : LowerBounded<A> {
        abstract override fun toSet(): Set<Bounded<A>>
        abstract override fun toString(): String

        final override fun contains(value: A): Boolean {
            val endExclusive = endExclusiveValue
            return value >= startValue && (endExclusive == null || value < endExclusive)
        }

        companion object {
            operator fun <A : Comparable<A>> invoke(
                start: A,
                endExclusive: A
            ): Bounded<A> {
                return if (start touches endExclusive) {
                    // TODO: Returner nullable eller either
                    require(start < endExclusive) {
                        "Start must be less than endExclusive"
                    }
                    Point(start, endExclusive)
                } else {
                    BoundedImpl(start, endExclusive)
                }
            }
        }
    }

    data class Point<A : Comparable<A>>(
        val value: A, override val endExclusiveValue: A?

    ) : Bounded<A>() {
        init {
            require(endExclusiveValue == null || value < endExclusiveValue) {
                "Start must be less than endExclusive"
            }
            require(value touches endExclusiveValue) {
                "Start must touch endExclusive"
            }
        }

        override val startValue: A get() = value

        override fun toIterableDomain(): Point<A> {
            return this
        }

        override fun toSet(): Set<Point<A>> {
            return setOf(toIterableDomain())
        }

        override fun toString(): String = "[$value]"
    }

    private data class LowerBoundedImpl<A : Comparable<A>>(
        override val startValue: A
    ) : LowerBounded<A> {

        override val endExclusiveValue: A? get() = null

        override fun contains(value: A): Boolean = value >= startValue

        override fun toIterableDomain(): LowerBounded<A> {
            return this
        }

        override fun toSet(): Set<LowerBounded<A>> {
            return setOf(toIterableDomain())
        }

        override fun toString(): String = "[$startValue..+∞)"
    }

    private data class UpperBoundedImpl<A : Comparable<A>>(
        override val endExclusiveValue: A
    ) : PartiallyBounded<A> {
        override val startValue: A? get() = null

        override fun contains(value: A): Boolean = value <= endExclusiveValue

        override fun toIterableDomain(): PartiallyBounded<A> {
            return this
        }

        override fun toSet(): Set<PartiallyBounded<A>> {
            return setOf(toIterableDomain())
        }

        override fun toString(): String = "(−∞..$endExclusiveValue)"
    }

    private data class BoundedImpl<A : Comparable<A>>(
        override val startValue: A,
        override val endExclusiveValue: A
    ) : Bounded<A>() {
        init {
            assert(startValue < endExclusiveValue) {
                "Start must be less than endInclusive"
            }
        }

        override fun toIterableDomain(): Bounded<A> {
            return this
        }

        override fun toSet(): Set<Bounded<A>> {
            return setOf(toIterableDomain())
        }

        override fun toString(): String = "[${startValue}..${endExclusiveValue})"
    }

    companion object {
        operator fun <A : Comparable<A>> invoke(firstEndpoint: Endpoint<A>, secondEndpoint: Endpoint<A>) : Range<A>? {
            val start = minOf(firstEndpoint, secondEndpoint)
            val endExclusive = maxOf(firstEndpoint, secondEndpoint)

            return when(start) {
                is Endpoint.Value -> when(endExclusive) {
                    is Endpoint.Value -> Bounded(start.value, endExclusive.value)
                    Endpoint.PositiveInfinity -> LowerBounded(start.value)
                    Endpoint.NegativeInfinity -> null
                }
                Endpoint.NegativeInfinity -> when(endExclusive) {
                    is Endpoint.Value -> UpperBoundedImpl(endExclusive.value)
                    Endpoint.PositiveInfinity -> Unbounded()
                    Endpoint.NegativeInfinity -> null
                }
                Endpoint.PositiveInfinity -> null
            }
        }


        operator fun <A : Comparable<A>> invoke(firstEndpoint: Endpoint.Start<A>, secondEndpoint: Endpoint.End<A>) : Range<A> {
            val start = minOf(firstEndpoint, secondEndpoint) as Endpoint.Start
            val endExclusive = maxOf(firstEndpoint, secondEndpoint) as Endpoint.End

            return when(start) {
                is Endpoint.Value -> when(endExclusive) {
                    is Endpoint.Value -> Bounded(start.value, endExclusive.value)
                    Endpoint.PositiveInfinity -> LowerBounded(start.value)
                }
                Endpoint.NegativeInfinity -> when(endExclusive) {
                    is Endpoint.Value -> UpperBoundedImpl(endExclusive.value)
                    Endpoint.PositiveInfinity -> Unbounded()
                }
            }
        }

        operator fun <A : Comparable<A>> invoke(
            start: A,
            endExclusive: A
        ): Bounded<A> {
            return Bounded(start, endExclusive)
        }
    }

    val startValue: A?
    val endExclusiveValue: A?

    fun toIterableDomain(): Range<A>

    override fun recursiveIterator(): Iterator<Range<A>> = object : Iterator<Range<A>> {
        private var current: Range<A>? = this@Range
        override fun hasNext(): Boolean {
            return current != null
        }

        override fun next(): Range<A> {
            val current = current
            return if (current != null) {
                this.current = null
                current
            } else {
                throw NoSuchElementException()
            }
        }
    }

    override fun <V> allMappedTo(value: V): RangeMap<A, V> {
        return rangeMapOf(this mappedTo value)
    }


    override fun contains(value: A): Boolean
}

@Suppress("UNCHECKED_CAST")
val <A : Comparable<A>> Range<A>.endEndpoint: Endpoint.End<A> get() = endExclusiveValue?.let { Endpoint.Value(it) } ?: (Endpoint.PositiveInfinity as Endpoint.End<A>)

@Suppress("UNCHECKED_CAST")
val <A : Comparable<A>> Range<A>.startEndpoint: Endpoint.Start<A> get() = startValue?.let { Endpoint.Value(it) } ?: (Endpoint.NegativeInfinity as Endpoint.Start<A>)

operator fun <A : Comparable<A>> Range<A>.component1(): Endpoint.Start<A> = startEndpoint

operator fun <A : Comparable<A>> Range<A>.component2(): Endpoint.End<A> = endEndpoint

infix fun <A : Comparable<A>> A.towards(endExclusive: A): Range<A> {
    val range = Range.invoke(this, endExclusive)
    return range
}

infix fun <A : Comparable<A>> Endpoint<A>.towards(endExclusive: Endpoint<A>): Range<A>? {
    return Range(this, endExclusive)
}

infix fun <A : Comparable<A>> Endpoint.Start<A>.towards(endExclusive: Endpoint.End<A>): Range<A> {
    return Range(this, endExclusive)
}

fun <A : Comparable<A>> atLeast(start: A): Range<A> {
    return Range.LowerBounded(start)
}

fun <A : Comparable<A>> atLeast(start: Endpoint<A>): Range<A>? {
    return Range(start, Endpoint.PositiveInfinity())
}

fun <A : Comparable<A>> atLeast(start: Endpoint.Start<A>): Range<A> {
    return Range(start, Endpoint.PositiveInfinity())
}

fun <A : Comparable<A>> lessThan(endExclusive: A): Range<A> {
    return Range(Endpoint.NegativeInfinity(), Endpoint.Value(endExclusive))
}

fun <A : Comparable<A>> lessThan(endExclusive: Endpoint.End<A>): Range<A> {
    return Range(Endpoint.NegativeInfinity(), endExclusive)
}

fun <A : Comparable<A>> lessThan(endExclusive: Endpoint<A>): Range<A>? {
    return when(endExclusive) {
        is Endpoint.Value -> lessThan(endExclusive.value)
        is Endpoint.End -> lessThan(endExclusive)
        Endpoint.NegativeInfinity -> null
    }
}

infix fun <A : Comparable<A>> Range<A>.rangeUnion(other: Range<A>): RangeSet.Scalar<A> {
    return if (startEndpoint <= other.startEndpoint) {
        if (other.startEndpoint <= endEndpoint) {
            Range(startEndpoint, maxOf(endEndpoint, other.endEndpoint))
        } else {
            rangeSetOf(this, other)
        }
    } else {
        if (startEndpoint <= other.endEndpoint) {
            Range(other.startEndpoint, maxOf(endEndpoint, other.endEndpoint))
        } else {
            rangeSetOf(this, other)
        }
    }
}

infix fun <A : Comparable<A>> Range<A>.rangeIntersect(other: Range<A>): Range<A>? {
    return if (startEndpoint <= other.startEndpoint) {
        if (other.startEndpoint < endEndpoint) {
            Range(other.startEndpoint, minOf(endEndpoint, other.endEndpoint))
        } else {
            null
        }
    } else {
        if (other.endEndpoint > startEndpoint) {
            Range(startEndpoint, minOf(endEndpoint, other.endEndpoint))
        } else {
            null
        }
    }
}

operator fun <A : Comparable<A>>  Range<A>.contains(endpoint: Endpoint<A>): Boolean {
    return when(endpoint) {
        is Endpoint.Value -> contains(endpoint.value)
        is Endpoint.NegativeInfinity -> startEndpoint == endpoint
        is Endpoint.PositiveInfinity -> endEndpoint == endpoint
    }
}

infix fun <A : Comparable<A>> Range<A>.rangeDifference(other: Range<A>): RangeSet.Scalar<A> {
    val newStart = maxOf(startEndpoint, other.startEndpoint)
    val newEndExclusive = minOf(endEndpoint, other.endEndpoint)
    return if (newStart < newEndExclusive) {
        if (newStart in this && newEndExclusive in this) {
            val backingRangeMap= TreeRangeMap(listOf(
                newStart towards (startEndpoint as Endpoint.Value) mappedTo true,
                (endEndpoint as Endpoint.Value) towards newEndExclusive mappedTo true
            )) { _, a, b -> a || b }
            RangeSet.ofMap(backingRangeMap)
        } else {
            Range(newStart, newEndExclusive)
        }
    } else {
        RangeSet.ofMap(emptyRangeMap())
    }
}