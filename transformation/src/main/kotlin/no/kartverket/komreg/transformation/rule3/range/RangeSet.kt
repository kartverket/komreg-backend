@file:Suppress("NOTHING_TO_INLINE")

package no.kartverket.komreg.transformation.rule3.range

import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.transformation.rule3.util.And

sealed interface RangeSet<A> {
    companion object {
        fun <A : Comparable<A>> ofMap(rangeMapLike: RangeMap<A, Boolean>): RangeSet.Scalar<A> {
            return OfMap(rangeMapLike)
        }
    }

    interface Scalar<A : Comparable<A>> : RangeSet<A>, Iterable<Range<A>> {
        fun toSet(): Set<Range<A>>
        override fun <B : Comparable<B>> zip(other: RangeSet<B>): Vector<A, B> {
            return Vector(this, other.scalar)
        }

        override fun recursiveIterator(): Iterator<Range<A>>
        override fun iterator(): Iterator<Range<A>> = recursiveIterator()



        override fun <V> allMappedTo(value: V): RangeMap<A, V>
    }

    @JvmInline
    private value class OfMap<A : Comparable<A>>(val backingMap: RangeMap<A, Boolean>) : Scalar<A> {

        override fun <V> allMappedTo(value: V): RangeMap<A, V> {
            return backingMap.mapValues { value }
        }

        override fun toSet(): Set<Range<A>> {
            // TODO: Kanskje noe mer effektivt enn å lage en ny map?
            return backingMap.toMap().keys.flatMapTo(HashSet()) { it.scalar }
        }

        override fun contains(value: A): Boolean {
            return backingMap[value] != null
        }

        override fun recursiveIterator(): Iterator<Range<A>> = iterator {
            for ((ranges, _) in backingMap) {
                for (range in ranges.scalar) {
                    yield(range)
                }
            }
        }
    }

    data class Vector<A, B : Comparable<B>>(
        val leftRangeSet: RangeSet<A>,
        val rightRangeSet: Scalar<B>
    ) : RangeSet<And<A, B>> {
        override fun contains(value: And<A, B>): Boolean {
            return leftRangeSet.contains(value.leftZipped) && rightRangeSet.contains(value.rightZipped)
        }

        override fun <C : Comparable<C>> zip(other: RangeSet<C>): Vector<And<A, B>, C> {
            return Vector(Vector(leftRangeSet, rightRangeSet), other.scalar)
        }

        override fun recursiveIterator(): Iterator<And<Any, Range<B>>> = iterator {
            for (left in leftRangeSet.recursiveIterator()) {
                for (right in rightRangeSet.recursiveIterator()) {
                    yield(And(left, right))
                }
            }
        }

        override fun <V> allMappedTo(value: V): RangeVectorMap<A, B, V> {
            val leftMapped = leftRangeSet allMappedTo value
            val backingVectorMap =
            rightRangeSet.fold(emptyRangeMap<B, MultiRangeMap<A, V>>()) { acc, bs ->
                acc.plus(bs, leftMapped) { _: Range<B>, _, _ ->
                    throw AssertionError("Should not happen")
                }
            }
            return RangeVectorMap(backingVectorMap)
        }
    }

    operator fun contains(value: A): Boolean

    infix fun <B : Comparable<B>> zip(other: RangeSet<B>): Vector<A, B>

    fun recursiveIterator(): Iterator<Any>

    infix fun <V> allMappedTo(value: V) : MultiRangeMap<A, V>

}

val x: OpenEndRange<String> = ""..< "st"

/**
 * All RangeSet<A> with A : Comparable<A> are RangeSet.Scalar<A> (since Zip is not Comparable)
 */
inline val <A : Comparable<A>> RangeSet<A>.scalar: RangeSet.Scalar<A> get() {
    return this as RangeSet.Scalar
}

/**
 * All RangeSet<A> with A : Zip<*,*> are RangeSet.Vector<A> (since Zip is not Comparable)
 */
inline val <A, B : Comparable<B>> RangeSet<And<A, B>>.vector: RangeSet.Vector<A, B> get() {
    return this as RangeSet.Vector
}


inline operator fun <A, B : Comparable<B>> RangeSet<A>.times(other: RangeSet<B>): RangeSet.Vector<A, B> {
    return zip(other.scalar)
}

fun <A : Comparable<A>> emptyRangeSet() = RangeSet.ofMap(emptyRangeMap<A, Boolean>())

fun <A : Comparable<A>> rangeSetOf(vararg ranges: Range<A>): RangeSet.Scalar<A> {
    val entries = ranges.map { it mappedTo true }.toTypedArray()
    return RangeSet.ofMap(rangeMapOf<A, Boolean>(*entries))
}

@JvmName("iterator1")
inline operator fun <
        A : Comparable<A>
        > RangeSet<A>.iterator(): Iterator<Range<A>> =
    scalar.recursiveIterator()

@Suppress("UNCHECKED_CAST")
@JvmName("iterator2")
inline operator fun <
        B : Comparable<B>,
        A : Comparable<A>
        > RangeSet<And<B, A>>.iterator(): Iterator<And<Range<B>, Range<A>>> =
    vector.recursiveIterator() as Iterator<And<Range<B>, Range<A>>>

@Suppress("UNCHECKED_CAST")
@JvmName("iterator3")
inline operator fun <
        C : Comparable<C>,
        B : Comparable<B>,
        A : Comparable<A>
        > RangeSet<And<And<C, B>, A>>.iterator(): Iterator<And<And<Range<C>, Range<B>>, Range<A>>> =
    vector.recursiveIterator() as Iterator<And<And<Range<C>, Range<B>>, Range<A>>>

@Suppress("UNCHECKED_CAST")
@JvmName("iterator4")
inline operator fun <
        D : Comparable<D>,
        C : Comparable<C>,
        B : Comparable<B>,
        A : Comparable<A>
        > RangeSet<And<And<And<D, C>, B>, A>>.iterator(): Iterator<And<And<And<Range<D>, Range<C>>, Range<B>>, Range<A>>> =
    vector.recursiveIterator() as Iterator<And<And<And<Range<D>, Range<C>>, Range<B>>, Range<A>>>

@Suppress("UNCHECKED_CAST")
@JvmName("iterator5")
inline operator fun <
        E : Comparable<E>,
        D : Comparable<D>,
        C : Comparable<C>,
        B : Comparable<B>,
        A : Comparable<A>
        > RangeSet<And<And<And<And<E, D>, C>, B>, A>>.iterator(): Iterator<And<And<And<And<Range<E>, Range<D>>, Range<C>>, Range<B>>, Range<A>>> =
    vector.recursiveIterator() as Iterator<And<And<And<And<Range<E>, Range<D>>, Range<C>>, Range<B>>, Range<A>>>

@Suppress("UNCHECKED_CAST")
@JvmName("iterator6")
inline operator fun <
        F : Comparable<F>,
        E : Comparable<E>,
        D : Comparable<D>,
        C : Comparable<C>,
        B : Comparable<B>,
        A : Comparable<A>
        > RangeSet<And<And<And<And<And<F, E>, D>, C>, B>, A>>.iterator(): Iterator<And<And<And<And<And<Range<F>, Range<E>>, Range<D>>, Range<C>>, Range<B>>, Range<A>>> =
    vector.recursiveIterator() as Iterator<And<And<And<And<And<Range<F>, Range<E>>, Range<D>>, Range<C>>, Range<B>>, Range<A>>>
