package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class Kommunenummer(val fylkesnummer: Fylkesnummer, val lopenummer: Lopenummer) {
    @Serializable
    data class Lopenummer(val value: Byte) : Comparable<Lopenummer> {
        override fun compareTo(other: Lopenummer): Int = value.compareTo(other.value)
    }

    companion object {
        operator fun invoke(value: Long): Kommunenummer {
            val (fylkesnummer, løpenummer) = value.splitToFylkesnummerOgLøpenummer()
            return Kommunenummer(
                Fylkesnummer(fylkesnummer.toLong()),
                Lopenummer(løpenummer.toByte()),
            )
        }
    }
}

private fun Long.splitToFylkesnummerOgLøpenummer() = this.toString()
    .padStart(4, '0')
    .takeLast(4)
    .chunked(2)

fun Kommunenummer.verdi(): String {
    val fylkesnummer = this.fylkesnummer.value.toString().padStart(2, '0')
    val kommuneløpenummer = this.lopenummer.value.toString().padStart(2, '0')
    return "$fylkesnummer$kommuneløpenummer"
}
