package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class Adressenummernummer(
    val value: Short
) : Comparable<Adressenummernummer> {
    override fun compareTo(other: Adressenummernummer): Int =
        value.compareTo(other.value)
}
