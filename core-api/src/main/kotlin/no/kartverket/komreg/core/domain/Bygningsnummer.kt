package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class Bygningsnummer(val value: Long) : Comparable<Bygningsnummer> {
    override fun compareTo(other: Bygningsnummer): Int {
        return value.compareTo(other.value)
    }
}
