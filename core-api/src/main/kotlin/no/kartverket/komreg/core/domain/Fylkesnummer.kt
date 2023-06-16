package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class Fylkesnummer(val value: Long)

fun Fylkesnummer.verdi() = value.toString().padStart(2, '0')
