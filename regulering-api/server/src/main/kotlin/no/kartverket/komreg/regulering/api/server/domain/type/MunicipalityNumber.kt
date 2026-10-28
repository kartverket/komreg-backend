package no.kartverket.komreg.regulering.api.server.domain

import kotlinx.serialization.KSerializer

@JvmInline
value class MunicipalityNumber private constructor (val value: String) {
    sealed interface Factory {
        operator fun invoke(value: String): MunicipalityNumber
    }
    object DefaultFactory : Factory {
        override fun invoke(value: String): MunicipalityNumber {
            require(value.length == 4 && value.all { it.isDigit() }) {
                "Municipality number must be a 4-digit string"
            }
            return MunicipalityNumber(value)
        }
    }
    object TestFactory : Factory {
        override fun invoke(value: String): MunicipalityNumber {
            require(value.length <= 19 && value.all { it.isDigit() }) {
                "Municipality number must be a string of digits with a maximum length of 19"
            }
            return MunicipalityNumber(value)
        }
    }

    sealed class Serializer(private val create: Factory) : KSerializer<MunicipalityNumber> {
        override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
            "MunicipalityNumber",
            kotlinx.serialization.descriptors.PrimitiveKind.STRING
        )

        override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: MunicipalityNumber) {
            encoder.encodeString(value.value)
        }

        override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): MunicipalityNumber {
            with(create) {
                return invoke(decoder.decodeString())
            }
        }
    }
    object DefaultSerializer : Serializer(DefaultFactory)
    object TestSerializer : Serializer(TestFactory)

    companion object {
        context(create: Factory)
        operator fun invoke(value: String) = create(value)

    }
}
