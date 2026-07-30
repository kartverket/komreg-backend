package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class Koordinat(
    val x: Double,
    val y: Double,
)