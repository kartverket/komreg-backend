package no.kartverket.komreg.regulering.reform.domain.type

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class TitleNumber(val value: Short) {
    init {
        require(value in RANGE) { "Title number must be between 0 and 9999, was $value" }
    }

    override fun toString(): String = value.toString()

    companion object {
        private val RANGE = 0..9999

        operator fun invoke(value: Int): TitleNumber {
            require(value in RANGE) { "Title number must be between 0 and 9999, was $value" }
            return TitleNumber(value.toShort())
        }
    }
}
