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

        return Comparator.nullsFirst(Comparator.naturalOrder<Char>()).compare(value, other.value)
    }
}
