package no.kartverket.komreg.core.domain

import no.kartverket.komreg.core.data.RawData

data class Matrikkelnummer(
    val kommunenummer: Kommunenummer,
    val gardsnummer: Gardsnummer,
    val bruksnummer: Bruksnummer,
    val festenummer: Festenummer?,
    val seksjonsnummer: Seksjonsnummer?,
) {
    data class Gardsnummer(val value: Int)
    data class Bruksnummer(val value: Short)
    data class Festenummer(val value: Short)
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
