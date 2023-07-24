package no.kartverket.komreg.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class Fylke(
    val fylkesnummer: Fylkesnummer,
    val fylkesnavn: Fylkesnavn,
    val gyldigTilDato: LocalDate?,
) {
    companion object {
        operator fun invoke(
            fylkesnummer: Long,
            fylkesnavn: String,
            gyldigTilDato: LocalDate?,
        ): Fylke =
            Fylke(
                Fylkesnummer(fylkesnummer),
                Fylkesnavn(fylkesnavn),
                gyldigTilDato,
            )
    }
}
