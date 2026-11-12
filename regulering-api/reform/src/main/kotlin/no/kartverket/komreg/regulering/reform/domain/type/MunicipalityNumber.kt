package no.kartverket.komreg.regulering.reform.domain.type

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

data class MunicipalityNumber(val county: CountyNumber, val serial: MunicipalitySerial) {
    val value: String get() = county.value + serial.value

    override fun toString(): String = value

    class Serializer(private val scheme: NumberingScheme) : KSerializer<MunicipalityNumber> {
        override val descriptor = PrimitiveSerialDescriptor("MunicipalityNumber", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: MunicipalityNumber) = encoder.encodeString(value.value)
        override fun deserialize(decoder: Decoder): MunicipalityNumber =
            with(scheme) { MunicipalityNumber(decoder.decodeString()) }
    }

    companion object {
        context(scheme: NumberingScheme)
        operator fun invoke(value: String): MunicipalityNumber {
            require(value.all { it.isDigit() } && value.length - 2 in scheme.countyDigits) {
                "Municipality number must be a digit string with a county part of length " +
                    scheme.countyDigits.sorted().joinToString(" or ") + " followed by 2 digits"
            }
            val countyLength = value.length - 2
            return MunicipalityNumber(CountyNumber(value.take(countyLength)), MunicipalitySerial(value.drop(countyLength)))
        }
    }
}
