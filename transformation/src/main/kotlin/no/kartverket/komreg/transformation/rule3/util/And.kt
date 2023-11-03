@file:Suppress("NOTHING_TO_INLINE")

package no.kartverket.komreg.transformation.rule3.util


sealed interface And<out A, out B>  {
    val leftZipped: A
    val rightZipped: B

    interface Pair<out A, out B> : And<A, B> {
        override val leftZipped: A
        override val rightZipped: B
    }

    interface NTuple<out Z : And<*, *>, B> : And<Z, B> {
        override val leftZipped: Z
    }

    infix fun <C : Comparable<C>> zip(c: C): And<And<A, B>, C>

    companion object {
        operator fun <A, B> invoke(
            a: A,
            b: B
        ): And<A, B> {
            return PairImpl(a, b)
        }
        fun eq(self: And<*, *>, other: Any?) : Boolean {
            return when(self) {
                is Pair<*, *> -> eq(self, other)
                is NTuple<*, *> -> eq(self, other)
            }
        }

        fun eq(self: Pair<*, *>, other: Any?): Boolean {
            if (this === other) return true
            if (other !is Pair<*, *>) return false
            return self.leftZipped == other.leftZipped && self.rightZipped == other.rightZipped
        }

        fun eq(self: NTuple<*, *>, other: Any?) : Boolean {
            if (self === other) return true
            if (other !is NTuple<*, *>) return false
            return eq(self.leftZipped, other.leftZipped) && self.rightZipped == other.rightZipped
        }

        fun hash(self: And<*, *>) : Int {
            return hash(self, 0)
        }

        private tailrec fun hash(self: And<*, *>, n: Int) : Int {
            return when(self) {
                is Pair<*, *> -> 32 * (32 * n + self.rightZipped.hashCode()) + self.leftZipped.hashCode()
                is NTuple<*, *> -> hash(self.leftZipped, 32 * n + self.rightZipped.hashCode())
            }
        }
    }
}

private data class PairImpl<A, B >(
    override val leftZipped: A,
    override val rightZipped: B
) : And.Pair<A, B> {
    override fun <C : Comparable<C>> zip(c: C): And<And<A, B>, C> {
        return NTupleImpl(this, c)
    }

    override fun equals(other: Any?): Boolean {
        return And.eq(this, other)
    }

    override fun hashCode(): Int {
        return And.hash(this)
    }

    override fun toString(): String {
        return "$leftZipped -> $rightZipped"
    }
}

private data class NTupleImpl<Z : And<*, A>, A, B>(
    override val leftZipped: Z,
    override val rightZipped: B
) : And.NTuple<Z, B> {
    override fun <C : Comparable<C>> zip(c: C): And<And<Z, B>, C> {
        return NTupleImpl(this, c)
    }
    override fun equals(other: Any?): Boolean {
        return And.eq(this, other)
    }

    override fun hashCode(): Int {
        return And.hash(this)
    }

    override fun toString(): String {
        return "$leftZipped -> $rightZipped"
    }
}

infix fun <A, B> A.zip(
    b: B
): And<A, B> = PairImpl(this, b)


@JvmName("component1_2")
inline operator fun <A, B> And<A, B>.component1(): A = leftZipped
@JvmName("component2_2")
inline operator fun <A, B> And<A, B>.component2(): B = rightZipped

@JvmName("component1_3")
inline operator fun <A, B, C> And<And<A, B>, C>.component1(): A  = leftZipped.leftZipped
@JvmName("component2_3")
inline operator fun <A, B, C> And<And<A, B>, C>.component2(): B =  leftZipped.rightZipped
@JvmName("component3_3")
inline operator fun <A, B, C> And<And<A, B>, C>.component3(): C =  rightZipped

@JvmName("component1_4")
inline operator fun <A, B, C, D> And<And<And<A, B>, C>, D>.component1(): A  =leftZipped.leftZipped.leftZipped
@JvmName("component2_4")
inline operator fun <A, B, C, D> And<And<And<A, B>, C>, D>.component2(): B = leftZipped.leftZipped.rightZipped
@JvmName("component3_4")
inline operator fun <A, B, C, D> And<And<And<A, B>, C>, D>.component3(): C = leftZipped.rightZipped
@JvmName("component4_4")
inline operator fun <A, B, C, D> And<And<And<A, B>, C>, D>.component4(): D = rightZipped

@JvmName("component1_5")
inline operator fun <A, B, C, D, E> And<And<And<And<A, B>, C>, D>, E>.component1(): A  =leftZipped.leftZipped.leftZipped.leftZipped
@JvmName("component2_5")
inline operator fun <A, B, C, D, E> And<And<And<And<A, B>, C>, D>, E>.component2(): B = leftZipped.leftZipped.leftZipped.rightZipped
@JvmName("component3_5")
inline operator fun <A, B, C, D, E> And<And<And<And<A, B>, C>, D>, E>.component3(): C = leftZipped.leftZipped.rightZipped
@JvmName("component4_5")
inline operator fun <A, B, C, D, E> And<And<And<And<A, B>, C>, D>, E>.component4(): D = leftZipped.rightZipped
@JvmName("component5_5")
inline operator fun <A, B, C, D, E> And<And<And<And<A, B>, C>, D>, E>.component5(): E = rightZipped
