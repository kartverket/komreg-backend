package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

/**
 * value == 0 dersom adressenummeret ikke har bokstav
 */
@Serializable
data class Adressenummerbokstav(
    val value: Char?
) : Comparable<Adressenummerbokstav> {

    override fun compareTo(other: Adressenummerbokstav): Int {

        return when {
            value == null && other.value == null -> 0
            value == null -> -1
            other.value == null -> 1
            else -> value.compareTo(other.value)
        }
    }
}
