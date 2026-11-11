package no.kartverket.komreg.regulering.reform.domain.type

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ParcelId(val value: Long) {
    override fun toString(): String = value.toString()
}
