package no.kartverket.komreg.core.domain

interface IdType<V : Any, Self> : Comparable<Self>, Comparator<V>

data class Id<V : Any> (
    val type: IdType<V, *>,
    val value: V,
) : Comparable<Id<*>> {
    override fun compareTo(other: Id<*>): Int {
        val myName = type::class.qualifiedName!!
        val otherName = other.type::class.qualifiedName!!

        val nameComparrison = myName.compareTo(otherName)
        return if (nameComparrison == 0) {
            if (type::class != other.type::class) {
                throw IllegalStateException("Multiple classes with name $myName")
            }

            val typeComparrison = compareType<Any>(type, other.type)
            if (typeComparrison == 0) {
                compareValue(type, value, other.value)
            } else {
                typeComparrison
            }
        } else {
            nameComparrison
        }
    }

    fun isOfType(otherType: IdType<*, *>): Boolean {
        return type == otherType
    }

    fun <RV : Any> typedValue(requestedType: IdType<RV, *>) : RV? {
        return if (isOfType(requestedType)) {
            value as RV
        } else {
            null
        }
    }

    companion object {
        private fun <V : Any> compareType(t1: IdType<*, *>, t2: IdType<*, *>): Int {
            return (t1 as IdType<V, Any>).compareTo(t2)
        }

        private fun <V : Any, T : IdType<V, *>> compareValue(type: T, v1: Any, v2: Any): Int {
            return type.compare(v1 as V, v2 as V)
        }
    }
}
