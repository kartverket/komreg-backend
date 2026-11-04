package no.kartverket.komreg.regulering.api.server.domain.type

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class TitleNumber(val value: Short) {
    init {
        require(value in 0..9999) { "Title number must be between 0 and 9999" }
    }
}
