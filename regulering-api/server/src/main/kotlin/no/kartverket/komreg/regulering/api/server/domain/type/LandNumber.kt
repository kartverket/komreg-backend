package no.kartverket.komreg.regulering.api.server.domain.type

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class LandNumber(val value: Int) {
    init {
        require(value in 0..99999) { "Land number must be between 0 and 99999" }
    }
}
