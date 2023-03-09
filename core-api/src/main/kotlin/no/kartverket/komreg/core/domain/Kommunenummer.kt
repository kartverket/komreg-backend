package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class Kommunenummer(val fylkesnummer: Fylkesnummer, val lopenummer: Lopenummer) {
    @Serializable
    data class Lopenummer(val value: Byte)

    companion object {
        operator fun invoke(value: Long): Kommunenummer = Kommunenummer(
            Fylkesnummer(value / 100L),
            Lopenummer((value % 100L).toByte()),
        )
    }
}

// Temporary class for use in development
data class OldToNewKommune(
    val oldKommune: Long,
    val newKommune: Long,
)
