package no.kartverket.komreg.parameter.data

import no.kartverket.komreg.parameter.data.HList.Empty
import no.kartverket.komreg.parameter.data.HList.Snoc
import no.kartverket.komreg.parameter.data.HList.Snoc.Companion.append
import kotlin.reflect.cast

sealed interface HList {
    fun toArray(): Array<Any>
    object Empty : HList {
        override fun toArray(): Array<Any> = emptyArray()
    }

    class Snoc<
            @Suppress("unused") out Init : HList,
            @Suppress("unused") out Last : Any
            > private constructor(private val value: Array<Any>) : HList {
        @Suppress("UNCHECKED_CAST")
        val init: Init get() = if (value.size >= 2) {
            Snoc<HList, Any>(value.copyOfRange(0, value.size - 1)) as Init
        } else {
            Empty as Init
        }

        @Suppress("UNCHECKED_CAST")
        val last: Last get() = value.last() as Last

        val size: Int = value.size

        fun append(other: Snoc<*, *>): Snoc<*, *> {
            val thisSize = value.size
            val updated = value.copyOf(thisSize + other.size)
            for (i in 0 until other.size) {
                updated[i+ thisSize] = other.value[i]
            }
            return Snoc<HList, Any>(updated as Array<Any>)
        }

        companion object {
            fun fromArray(array: Array<out Any>): HList {
                return if (array.isNotEmpty()) {
                    Snoc<HList, Any>(array.map { id -> id }.toTypedArray())
                } else {
                    Empty
                }
            }
            operator fun <Last : Any> invoke(last: Last): Snoc<Empty, Last> =
                Snoc(arrayOf<Any>(last))

            fun <Init : Snoc<*, *>, Last : Any> Init.append(last: Last): Snoc<Init, Last> {
                val updatedArray = value.copyOf(value.size + 1)
                updatedArray[value.size] = last
                @Suppress("UNCHECKED_CAST") return Snoc(updatedArray as Array<Any>)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Snoc<*, *>

            return value.contentEquals(other.value)
        }

        override fun hashCode(): Int {
            return value.contentHashCode()
        }

        override fun toString(): String {
            return value.joinToString(prefix = "(", postfix = ")")
        }

        override fun toArray(): Array<Any> {
            return value.copyOf()
        }
    }

    companion object {
        operator fun <Last : Any> times(last: Last): Snoc<Empty, Last> = Snoc(last)
        fun fromArray(array: Array<out Any>): HList = Snoc.fromArray(array)
    }
}

operator fun <Init : HList, Last : Any> Init.times(last: Last): Snoc<Init, Last> {
    @Suppress("UNCHECKED_CAST") return when (this) {
        Empty -> Snoc(last) as Snoc<Init, Last>
        is Snoc<*, *> -> this.append(last)
    }
}

@Suppress("NOTHING_TO_INLINE")
inline operator fun <Init : Snoc<*, *>, Last : Any> Init.times(last: Last): Snoc<Init, Last> =
    this.append(last)

@Suppress("NOTHING_TO_INLINE")
inline operator fun <Last : Any> Empty.times(last: Last): Snoc<Empty, Last> = Snoc(last)

@Suppress("NOTHING_TO_INLINE")
inline fun <Last : Any> Last.toHList(): Snoc<Empty, Last> = Snoc(this)
