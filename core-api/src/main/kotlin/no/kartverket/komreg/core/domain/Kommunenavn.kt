package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class Kommunenavn(private val name: String) {
    init {
        require(name.isUppercase()) { "Kommunenavn skal skrives med store bokstaver" }
    }
}

fun String.isUppercase(): Boolean {
    return this == this.uppercase()
}
