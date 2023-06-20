package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class Fylkesnummer(val value: Long) : Comparable<Fylkesnummer> {
    override fun compareTo(other: Fylkesnummer): Int = value.compareTo(other.value)
}

fun Fylkesnummer.verdi() = value.toString().padStart(2, '0')
