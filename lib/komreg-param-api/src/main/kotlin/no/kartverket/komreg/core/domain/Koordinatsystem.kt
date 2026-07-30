package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Koordinatsystem {
    UTM32,
    UTM33,
    UTM35,
}