package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class Kretsnummer(
    val value: Long
) : Comparable<Kretsnummer> {
    override fun compareTo(other: Kretsnummer): Int =
        value.compareTo(other.value)
}
