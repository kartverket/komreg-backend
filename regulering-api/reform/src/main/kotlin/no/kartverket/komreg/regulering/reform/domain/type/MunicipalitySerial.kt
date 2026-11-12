package no.kartverket.komreg.regulering.reform.domain.type

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
value class MunicipalitySerial private constructor(val value: String) {
    override fun toString(): String = value

    object Serializer : KSerializer<MunicipalitySerial> {
        override val descriptor = PrimitiveSerialDescriptor("MunicipalitySerial", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: MunicipalitySerial) = encoder.encodeString(value.value)
        override fun deserialize(decoder: Decoder): MunicipalitySerial = MunicipalitySerial(decoder.decodeString())
    }

    companion object {
        operator fun invoke(value: String): MunicipalitySerial {
            require(value.length == 2 && value.all { it.isDigit() }) {
                "Municipality serial must be a 2-digit string"
            }
            return MunicipalitySerial(value)
        }
    }
}
