package no.kartverket.komreg.regulering.api.server.domain.type

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class AddressCode(val value: Int) {
    init {
        require(value in 1..99998) { "Unit number must be between 1 and 99998" }
    }
}
