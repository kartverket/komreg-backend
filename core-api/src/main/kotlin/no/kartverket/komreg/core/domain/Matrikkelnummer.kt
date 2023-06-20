package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class Matrikkelnummer(
    val kommunenummer: Kommunenummer,
    val gardsnummer: Gardsnummer,
    val bruksnummer: Bruksnummer,
    val festenummer: Festenummer?,
    val seksjonsnummer: Seksjonsnummer?,
) {
    @Serializable
    data class Gardsnummer(val value: Int) : Comparable<Gardsnummer> {
        override fun compareTo(other: Gardsnummer): Int = value.compareTo(other.value)
    }

    @Serializable
    data class Bruksnummer(val value: Short) : Comparable<Bruksnummer> {
        override fun compareTo(other: Bruksnummer): Int = value.compareTo(other.value)
    }

    @Serializable
    data class Festenummer(val value: Short) : Comparable<Festenummer> {
        override fun compareTo(other: Festenummer): Int = value.compareTo(other.value)
    }

    @Serializable
    data class Seksjonsnummer(val value: Short) : Comparable<Seksjonsnummer> {
        override fun compareTo(other: Seksjonsnummer): Int = value.compareTo(other.value)
    }

    companion object {
        operator fun invoke(
            kommuenummer: Long,
            gardsnummer: Long,
            bruksnummer: Long,
            festenummer: Long?,
            seksjonsnummer: Long?,
        ): Matrikkelnummer =
            Matrikkelnummer(
                Kommunenummer(kommuenummer),
                Gardsnummer(gardsnummer.toInt()),
                Bruksnummer(bruksnummer.toShort()),
                festenummer?.let { Festenummer(it.toShort()) },
                seksjonsnummer?.let { Seksjonsnummer(it.toShort()) },
            )
    }

    val fylkesnummer: Fylkesnummer = kommunenummer.fylkesnummer
}
