package no.kartverket.komreg

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

/**
 * Fra [https://github.com/cph-cachet/carp.core-kotlin/blob/develop/carp.common/src/commonMain/kotlin/dk/cachet/carp/common/infrastructure/serialization/PolymorphicEnumSerializer.kt](https://github.com/cph-cachet/carp.core-kotlin/blob/develop/carp.common/src/commonMain/kotlin/dk/cachet/carp/common/infrastructure/serialization/PolymorphicEnumSerializer.kt)
 * nevnt i [https://github.com/Kotlin/kotlinx.serialization/issues/1486](https://github.com/Kotlin/kotlinx.serialization/issues/1486)
 */
class PolymorphicEnumSerializer<T : Enum<T>>(private val enumSerializer: KSerializer<T>) : KSerializer<T> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(enumSerializer.descriptor.serialName)
    {
        element("value", enumSerializer.descriptor)
    }

    override fun deserialize(decoder: Decoder): T =
        decoder.decodeStructure(descriptor)
        {
            decodeElementIndex(descriptor)
            decodeSerializableElement(descriptor, 0, enumSerializer)
        }

    override fun serialize(encoder: Encoder, value: T) =
        encoder.encodeStructure(descriptor)
        {
            encodeSerializableElement(descriptor, 0, enumSerializer, value)
        }
}
