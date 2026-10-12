package no.kartverket.komreg.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Kretsnummer")
data class Kretsnummer(
    val value: Long
) : Comparable<Kretsnummer> {
    override fun compareTo(other: Kretsnummer): Int =
        value.compareTo(other.value)
}
