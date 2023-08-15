package no.kartverket.komreg.core.domain

import kotlinx.serialization.*
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.*

interface IdType<V : @Contextual Any, Self : @Contextual Any> : Comparable<Self>, Comparator<V> {
    val valueSerializer: KSerializer<V>
}

@Serializable(IdSerializer::class)
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
        operator fun <V : @Contextual Any> invoke(type: IdType<V, *>, value: V): Id {
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

@OptIn(ExperimentalSerializationApi::class)
class IdSerializer : KSerializer<Id> {
    private val typeSerializer = PolymorphicSerializer(IdType::class)

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("id") {
        element("type", typeSerializer.descriptor)
        element("value", NothingSerializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: Id) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, typeSerializer, value.type)

            fun <V : Any> IdType<V, *>.encode(v: Any) {
                encodeSerializableElement(descriptor, 1, valueSerializer, v as V)
            }

            value.type.encode(value.value)
        }
    }

    override fun deserialize(decoder: Decoder): Id {
        return decoder.decodeStructure(descriptor) {
            lateinit var type: IdType<out Any, *>
            lateinit var idValue: Any
            while (true) {
                val i = decodeElementIndex(descriptor)
                when (i) {
                    0 -> type = decodeSerializableElement(descriptor, 0, typeSerializer)
                    1 -> idValue = decodeSerializableElement(descriptor, 1, type.valueSerializer)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unknown index $i")
                }
            }
            Id(type, idValue)
        }
    }
}
