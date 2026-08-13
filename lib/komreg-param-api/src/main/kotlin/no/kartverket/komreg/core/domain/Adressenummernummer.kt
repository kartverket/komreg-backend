package no.kartverket.komreg.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.kartverket.komreg.parameter.data.EnumerableType
import no.kartverket.komreg.parameter.data.Enumerator
import no.kartverket.komreg.parameter.data.FinalType

@Serializable
@SerialName("Adressenummernummer")
data class Adressenummernummer(
    val value: Short
) : Comparable<Adressenummernummer> {
    override fun compareTo(other: Adressenummernummer): Int =
        value.compareTo(other.value)

    object Type : EnumerableType<Adressenummernummer> {
        override val enumerator: Enumerator<Adressenummernummer>
            get() = TODO("Not yet implemented")
        override val finalType: FinalType<Adressenummernummer>
            get() = FinalType<Adressenummernummer>()

    }
}
