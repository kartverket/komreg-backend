package no.kartverket.komreg.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.kartverket.komreg.parameter.data.EnumerableType
import no.kartverket.komreg.parameter.data.Enumerator
import no.kartverket.komreg.parameter.data.FinalType
import no.kartverket.komreg.parameter.data.FinalType.Companion.invoke

@Serializable
@SerialName("Kretsnummer")
data class Kretsnummer(
    val value: Long
) : Comparable<Kretsnummer> {
    override fun compareTo(other: Kretsnummer): Int =
        value.compareTo(other.value)

    object Type : EnumerableType<Kretstype> {
        override val enumerator: Enumerator<Kretstype>
            get() = TODO("Not yet implemented")
        override val finalType: FinalType<Kretstype>
            get() = FinalType<Kretstype>()
    }

}
