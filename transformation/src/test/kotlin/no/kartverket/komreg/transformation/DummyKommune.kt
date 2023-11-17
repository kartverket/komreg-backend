package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.domain.*

fun dummyKommune(fylkesnummer: Int, lopenummer: Int): Kommune {
    return Kommune(
        kommunenummer = Kommunenummer(
            Fylkesnummer(fylkesnummer.toLong()),
            Kommunenummer.Lopenummer(lopenummer.toByte()),
        ),
        kommunenavn = Kommunenavn("Dummy"),
        koordinatsystem = Koordinatsystem.UTM32,
        senterpunkt = Koordinat(123.0, 456.0),
        nedsattKonsesjonsgrense = false,
        godkjenteGardsnumre = "1,2,3",
        gyldigTilDato = null,
        adresse = null,
        standardRekvirent = null,
        kommunevapen = null,
    )
}
