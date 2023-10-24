package no.kartverket.komreg.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Kommunenummer")
data class Kommunenummer(val fylkesnummer: Fylkesnummer, val lopenummer: Lopenummer) {
    @Serializable
    @SerialName("Kommunelopenummer")
    data class Lopenummer(val value: Byte) : Comparable<Lopenummer> {
        override fun compareTo(other: Lopenummer): Int = value.compareTo(other.value)
    }

    companion object {
        operator fun invoke(value: Long): Kommunenummer {
            val (fylkesnummer, lopenummer) = value.splitToFylkesnummerOgLopenummer()
            return Kommunenummer(
                Fylkesnummer(fylkesnummer.toLong()),
                Lopenummer(lopenummer.toByte()),
            )
        }
    }
}

private fun Long.splitToFylkesnummerOgLopenummer() = this.toString()
    .padStart(4, '0')
    .takeLast(4)
    .chunked(2)

fun Kommunenummer.verdi(): String {
    val fylkesnummer = this.fylkesnummer.verdi()
    val kommunelopenummer = this.lopenummer.verdi()
    return "$fylkesnummer$kommunelopenummer"
}

fun Kommunenummer.Lopenummer.verdi(): String {
    return this.value.toString().padStart(2, '0')
}
