package no.kartverket.komreg.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class Kommune(
    val kommunenummer: Kommunenummer,
    val kommunenavn: Kommunenavn,
    val gyldigTilDato: LocalDate?,
) {
    companion object {
        operator fun invoke(
            kommunenummer: Long,
            kommunenavn: String,
            gyldigTilDato: LocalDate?,
        ): Kommune =
            Kommune(
                Kommunenummer(kommunenummer),
                Kommunenavn(kommunenavn),
                gyldigTilDato,
            )
    }
}
