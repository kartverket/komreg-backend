package no.kartverket.komreg.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.kartverket.komreg.parameter.data.FinalType

@Serializable
@SerialName("Kretstype")
data class Kretstype(
    val kretstype: String
) : Comparable<Kretstype> {
    override fun compareTo(other: Kretstype): Int = kretstype.compareTo(other.kretstype)

    companion object {
        val type = FinalType<Kretstype>()
    }
}
