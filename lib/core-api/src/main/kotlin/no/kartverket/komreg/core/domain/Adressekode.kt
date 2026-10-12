package no.kartverket.komreg.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Adressekode")
data class Adressekode(
    val value: Int
) : Comparable<Adressekode> {
    override fun compareTo(other: Adressekode): Int =
        value.compareTo(other.value)
}
