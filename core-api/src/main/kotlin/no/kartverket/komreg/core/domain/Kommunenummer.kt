package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class Kommunenummer(val fylkesnummer: Fylkesnummer, val lopenummer: Lopenummer) {
    @Serializable
    data class Lopenummer(val value: Byte)

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
