package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.data.RawData

@Serializable
data class Fylke(
    val fylkesnummer: Fylkesnummer,
    val fylkesnavn: Fylkesnavn,
) {
    companion object {
        operator fun invoke(
            fylkesnummer: Long,
            fylkesnavn: String,
        ): RawData<Fylke> =
            RawData(
                Fylke(
                    Fylkesnummer(fylkesnummer),
                    Fylkesnavn(fylkesnavn),
                ),
            )
    }
}
