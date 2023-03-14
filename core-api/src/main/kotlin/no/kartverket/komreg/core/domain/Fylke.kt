package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class Fylke(
    val fylkesnummer: Fylkesnummer,
    val fylkesnavn: Fylkesnavn,
) {
    companion object {
        operator fun invoke(
            fylkesnummer: Long,
            fylkesnavn: String,
        ): Fylke =
            Fylke(
                Fylkesnummer(fylkesnummer),
                Fylkesnavn(fylkesnavn)
            )
    }
}
