package no.kartverket.komreg.regulering.reform.domain.type

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class AddressNumber(val value: Short) {
    init {
        require(value in 1..9998) { "Unit number must be between 1 and 9998" }
    }
}
