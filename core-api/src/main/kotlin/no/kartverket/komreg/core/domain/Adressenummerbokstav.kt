package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

/**
 * value == 0 dersom adressenummeret ikke har bokstav
 */
@Serializable
data class Adressenummerbokstav(
    val value: Char
) : Comparable<Adressenummerbokstav> {
    companion object {
        const val NONE = '\u0000'
    }

    override fun compareTo(other: Adressenummerbokstav): Int =
        value.compareTo(other.value)
}
