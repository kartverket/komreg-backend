package no.kartverket.komreg.regulering.reform.domain.type

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
value class CountyNumber private constructor(val value: String) {
    override fun toString(): String = value

    class Serializer(private val scheme: NumberingScheme) : KSerializer<CountyNumber> {
        override val descriptor = PrimitiveSerialDescriptor("CountyNumber", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: CountyNumber) = encoder.encodeString(value.value)
        override fun deserialize(decoder: Decoder): CountyNumber =
            with(scheme) { CountyNumber(decoder.decodeString()) }
    }

    companion object {
        context(scheme: NumberingScheme)
        operator fun invoke(value: String): CountyNumber {
            require(value.length in scheme.countyDigits && value.all { it.isDigit() }) {
                "County number must be a digit string of length ${scheme.countyDigits.sorted().joinToString(" or ")}"
            }
            return CountyNumber(value)
        }
    }
}
