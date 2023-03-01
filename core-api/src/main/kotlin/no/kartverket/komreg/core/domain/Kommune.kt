package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.data.RawData

@Serializable
data class Kommune(
    val kommunenummer: Kommunenummer,
    val kommunenavn: Kommunenavn,
) {
    companion object {
        operator fun invoke(
            kommunenummer: Long,
            kommunenavn: String,
        ): RawData<Kommune> =
            RawData(
                Kommune(
                    Kommunenummer(kommunenummer),
                    Kommunenavn(kommunenavn),
                ),
            )
    }
}
