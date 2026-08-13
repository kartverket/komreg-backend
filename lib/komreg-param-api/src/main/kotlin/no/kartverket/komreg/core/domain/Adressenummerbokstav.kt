package no.kartverket.komreg.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.kartverket.komreg.parameter.data.EnumerableType
import no.kartverket.komreg.parameter.data.Enumerator
import no.kartverket.komreg.parameter.data.FinalType
import no.kartverket.komreg.parameter.data.FinalType.Companion.invoke

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

    object Type : EnumerableType<Adressenummerbokstav> {
        override val enumerator: Enumerator<Adressenummerbokstav>
            get() = TODO("Not yet implemented")
        override val finalType: FinalType<Adressenummerbokstav>
            get() = FinalType<Adressenummerbokstav>()

    }

}
