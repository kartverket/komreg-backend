package no.kartverket.komreg.transformation.rule3.range

sealed interface Endpoint<A : Comparable<A>> : Comparable<Endpoint<A>> {
    sealed interface Start<A : Comparable<A>> : Endpoint<A>
    sealed interface End<A : Comparable<A>> : Endpoint<A>

    data object PositiveInfinity : End<Nothing> {
        override val value: Nothing? get() = null
        override fun compareTo(other: Endpoint<Nothing>): Int {
            return if (other is PositiveInfinity) {
                0
            } else {
                1
            }
        }

        override fun toString(): String = "+∞"

        @Suppress("UNCHECKED_CAST")
        operator fun <A : Comparable<A>> invoke(): End<A> = this as End<A>
    }

    data object NegativeInfinity : Start<Nothing> {
        override val value: Nothing? get() = null
        override fun compareTo(other: Endpoint<Nothing>): Int {
            return if (other is NegativeInfinity) {
                0
            } else {
                -1
            }
        }

        override fun toString(): String = "−∞"

        @Suppress("UNCHECKED_CAST")
        operator fun <A : Comparable<A>> invoke(): Start<A> = this as Start<A>
    }

    @JvmInline
    value class Value<A : Comparable<A>>(override val value: A) : Start<A>, End<A> {
        override fun compareTo(other: Endpoint<A>): Int {
            return when (other) {
                PositiveInfinity -> -1
                is Value -> value.compareTo(other.value)
                NegativeInfinity -> 1
            }
        }

        override fun toString(): String = value.toString()
    }

    val value: A?
}