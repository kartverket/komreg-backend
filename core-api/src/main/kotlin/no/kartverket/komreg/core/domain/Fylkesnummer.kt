package no.kartverket.komreg.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.data.PartialNext

@Serializable
@SerialName("Fylkesnummer")
data class Fylkesnummer(val value: Long) : PartialNext<Fylkesnummer> {
    override fun compareTo(other: Fylkesnummer): Int = value.compareTo(other.value)
    override val next: Fylkesnummer?
        get() = if (value == Long.MAX_VALUE) null else Fylkesnummer(value + 1L)

    override fun toString(): String {
        return "Fylke(${verdi()})"
    }
}

fun Fylkesnummer.verdi() = value.toString().padStart(2, '0')
