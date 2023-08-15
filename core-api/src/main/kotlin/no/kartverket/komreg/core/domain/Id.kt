package no.kartverket.komreg.core.domain

interface IdType<V : Any, Self> : Comparable<Self>, Comparator<V>

data class Id internal constructor(
    internal val type: IdType<out Any, *>,
    internal val value: Any,
) : Comparable<Id> {
    override fun compareTo(other: Id): Int {
        val myName = type::class.qualifiedName!!
        val otherName = other.type::class.qualifiedName!!

        val nameComparison = myName.compareTo(otherName)
        return if (nameComparison == 0) {
            if (type::class != other.type::class) {
                throw IllegalStateException("Multiple classes with name $myName")
            }

            val typeComparrison = compareType<Any>(type, other.type)
            if (typeComparrison == 0) {
                type.compareValue(value, other.value)
            } else {
                typeComparrison
            }
        } else {
            nameComparison
        }
    }

    fun isOfType(otherType: IdType<*, *>): Boolean {
        return type == otherType
    }

    fun <RV : Any> typedValue(requestedType: IdType<RV, *>): RV? {
        return if (isOfType(requestedType)) {
            value as RV
        } else {
            null
        }
    }

    companion object {
        operator fun <V : Any> invoke(type: IdType<V, *>, value: V): Id {
            return Id(type, value)
        }

        private fun <V : Any> compareType(t1: IdType<*, *>, t2: IdType<*, *>): Int {
            return (t1 as IdType<V, Any>).compareTo(t2)
        }

        private fun <V : Any> IdType<V, *>.compareValue(v1: Any, v2: Any): Int {
            return compare(v1 as V, v2 as V)
        }
    }
}
