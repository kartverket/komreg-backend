package no.kartverket.komreg.regulering.api.server.domain.type

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class LeaseNumber(val value: Short) {
    init {
        require(value in 1..9999) { "Lease number must be between 1 and 9999" }
    }
}
