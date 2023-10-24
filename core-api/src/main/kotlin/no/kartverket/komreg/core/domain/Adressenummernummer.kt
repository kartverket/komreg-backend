package no.kartverket.komreg.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Adressenummernummer")
data class Adressenummernummer(
    val value: Short
) : Comparable<Adressenummernummer> {
    override fun compareTo(other: Adressenummernummer): Int =
        value.compareTo(other.value)
}
