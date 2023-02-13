package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.data.RawData

@Serializable
data class Matrikkelnummer(
    val kommunenummer: Kommunenummer,
    val gardsnummer: Gardsnummer,
    val bruksnummer: Bruksnummer,
    val festenummer: Festenummer?,
    val seksjonsnummer: Seksjonsnummer?,
) {
    @Serializable
    data class Gardsnummer(val value: Int)

    @Serializable
    data class Bruksnummer(val value: Short)

    @Serializable
    data class Festenummer(val value: Short)

    @Serializable
    data class Seksjonsnummer(val value: Short)

    companion object {
        operator fun invoke(
            kommuenummer: Long,
            gardsnummer: Long,
            bruksnummer: Long,
            festenummer: Long?,
            seksjonsnummer: Long?,
        ): RawData<Matrikkelnummer> =
            RawData(
                Matrikkelnummer(
                    Kommunenummer(kommuenummer),
                    Gardsnummer(gardsnummer.toInt()),
                    Bruksnummer(bruksnummer.toShort()),
                    festenummer?.let { Festenummer(it.toShort()) },
                    seksjonsnummer?.let { Seksjonsnummer(it.toShort()) }
                )
            )
    }

    val fylkesnummer: Fylkesnummer = kommunenummer.fylkesnummer
}
