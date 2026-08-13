package no.kartverket.komreg.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.kartverket.komreg.parameter.data.EnumerableType
import no.kartverket.komreg.parameter.data.Enumerator
import no.kartverket.komreg.parameter.data.FinalType

@Serializable
@SerialName("Matrikkelnummer")
data class Matrikkelnummer(
    val kommunenummer: Kommunenummer,
    val gardsnummer: Gardsnummer,
    val bruksnummer: Bruksnummer,
    val festenummer: Festenummer?,
    val seksjonsnummer: Seksjonsnummer?,
) {
    @Serializable
    @SerialName("Gardsnummer")
    data class Gardsnummer(val value: Int) : Comparable<Gardsnummer> {
        override fun compareTo(other: Gardsnummer): Int = value.compareTo(other.value)
        override fun toString(): String {
            return "Gård($value)"
        }

        object Type : EnumerableType<Gardsnummer> {
            override val enumerator: Enumerator<Gardsnummer>
                get() = TODO("Not yet implemented")
            override val finalType: FinalType<Gardsnummer> = FinalType<Gardsnummer>()
        }
    }

    @Serializable
    @SerialName("Bruksnummer")
    data class Bruksnummer(val value: Short) : Comparable<Bruksnummer> {
        override fun compareTo(other: Bruksnummer): Int = value.compareTo(other.value)
        object Type : EnumerableType<Bruksnummer> {
            override val enumerator: Enumerator<Bruksnummer>
                get() = TODO("Not yet implemented")
            override val finalType: FinalType<Bruksnummer> = FinalType<Bruksnummer>()
        }
    }

    @Serializable
    @SerialName("Festenummer")
    data class Festenummer(val value: Short) : Comparable<Festenummer> {
        override fun compareTo(other: Festenummer): Int = value.compareTo(other.value)
        object Type : EnumerableType<Festenummer> {
            override val enumerator: Enumerator<Festenummer>
                get() = TODO("Not yet implemented")
            override val finalType: FinalType<Festenummer>
                get() = FinalType<Festenummer>()

        }
    }

    @Serializable
    @SerialName("Seksjonsnummer")
    data class Seksjonsnummer(val value: Short) : Comparable<Seksjonsnummer> {
        override fun compareTo(other: Seksjonsnummer): Int = value.compareTo(other.value)
        object Type : EnumerableType<Seksjonsnummer> {
            override val enumerator: Enumerator<Seksjonsnummer>
                get() = TODO("Not yet implemented")
            override val finalType: FinalType<Seksjonsnummer>
                get() = FinalType<Seksjonsnummer>()

        }
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
