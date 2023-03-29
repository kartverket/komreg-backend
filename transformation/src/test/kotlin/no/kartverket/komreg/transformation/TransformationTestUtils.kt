package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Ident

fun kommunenummer(kommunenummer: String): Ident {
    val fylkesnummer = kommunenummer.substring(0, kommunenummer.length - 2)
    val kommunelopenummer = kommunenummer.substring(kommunenummer.length - 2)

    return Ident(
        Fylkesnummer(fylkesnummer.toLong(10)),
        Kommunenummer.Lopenummer(kommunelopenummer.toByte(10)),
    )
}

fun matrikkelnummer(
    kommunenummer: String,
    gardsnummer: Int,
    bruksnummer: Short,
    festenummer: Short = 0,
    seksjonsnummer: Short = 0,
): Ident {
    val fylkesnummer = kommunenummer.substring(0, kommunenummer.length - 2)
    val kommunelopenummer = kommunenummer.substring(kommunenummer.length - 2)

    return Ident(
        Fylkesnummer(fylkesnummer.toLong(10)),
        Kommunenummer.Lopenummer(kommunelopenummer.toByte(10)),
        Matrikkelnummer.Gardsnummer(gardsnummer),
        Matrikkelnummer.Bruksnummer(bruksnummer),
        Matrikkelnummer.Festenummer(festenummer),
        Matrikkelnummer.Seksjonsnummer(seksjonsnummer)
    )
}
