package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class Kretstype(
    val kretstype: String
) : Comparable<Kretstype> {
    override fun compareTo(other: Kretstype): Int = kretstype.compareTo(other.kretstype)
}
