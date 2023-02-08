package no.kartverket.komreg.core.domain

data class Kommunenummer(val fylkesnummer: Fylkesnummer, val lopenummer: Lopenummer) {
    data class Lopenummer(val value: Byte)

    companion object {
        operator fun invoke(value: Long): Kommunenummer = Kommunenummer(
            Fylkesnummer(value / 100L),
            Lopenummer((value % 100L).toByte())
        )
    }
}
