package no.kartverket.komreg.regulering.api.server.domain.type

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class UnitNumber(val value: Short) {
    init {
        require(value in 1..9999) { "Unit number must be between 1 and 9999" }
    }
}
