package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class Kommune(
    val kommunenummer: Kommunenummer,
    val kommunenavn: Kommunenavn,
) {
    companion object {
        operator fun invoke(
            kommunenummer: Long,
            kommunenavn: String,
        ): Kommune =
            Kommune(
                Kommunenummer(kommunenummer),
                Kommunenavn(kommunenavn)
            )
    }
}
