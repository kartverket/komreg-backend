package no.kartverket.komreg.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * value == 0 dersom adressenummeret ikke har bokstav
 */
@Serializable
@SerialName("Adressenummerbokstav")
data class Adressenummerbokstav(
    val value: Char?
) : Comparable<Adressenummerbokstav> {

    override fun compareTo(other: Adressenummerbokstav): Int {

        return Comparator.nullsFirst(Comparator.naturalOrder<Char>()).compare(value, other.value)
    }
}
