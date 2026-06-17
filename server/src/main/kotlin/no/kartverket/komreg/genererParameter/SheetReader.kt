package regulering.parameterfil

import regulering.model.KretsRad
import regulering.model.MatrikkelRad
import regulering.model.SheetData
import regulering.model.TeigRad
import regulering.model.VegRad

fun lesVegRader(sheet: SheetData): List<VegRad> {
    val k = VegKolonner.fra(sheet.headers)
    return sheet.linjer.map { rad ->
        VegRad(
            kommunenummer = rad.fraOgTil(k.dagensKommunenummer, k.nyttKommunenummer),
            adressekode = rad.fraOgTil(k.dagensAdressekode, k.nyAdressekode),
            adressenavn = rad.fraOgTil(k.dagensAdressenavn, k.nyttAdressenavn),
            adressenummer = rad.fraOgTil(k.dagensAdressenummer, k.nyttAdressenummer),
        )
    }
}

fun lesKretsRader(sheet: SheetData): List<KretsRad> {
    val k = KretsKolonner.fra(sheet.headers)
    return sheet.linjer.map { rad ->
        KretsRad(
            kommunenummer = rad.fraOgTil(k.kommunenummer, k.kommunenummer),
            kretsnummer = rad.fraOgTil(k.dagensKretsnummer, k.nyttKretsnummer),
            kretstype = rad.fraOgTil(k.dagensKretstype, k.nyKretstype),
        )
    }
}

fun lesTeigRader(sheet: SheetData): List<TeigRad> {
    val k = TeigKolonner.fra(sheet.headers)
    return sheet.linjer.map { rad ->
        TeigRad(
            kommunenummer = rad.fraOgTil(k.dagensKommunenummer, k.nyttKommunenummer),
            teigId = rad.fraOgTil(k.dagensTeigId, k.nyttTeigId),
        )
    }
}

fun lesMatrikkelRader(sheet: SheetData): List<MatrikkelRad> {
    val k = MatrikkelKolonner.fra(sheet.headers)
    return sheet.linjer.map { rad ->
        MatrikkelRad(
            kommunenummer = rad.fraOgTil(k.dagensKommunenummer, k.nyttKommunenummer),
            gardsnummer = rad.fraOgTil(k.dagensGardsnummer, k.nyttGardsnummer),
            bruksnummer = rad.fraOgTil(k.dagensBreuksnummer, k.nyttBruksnummer),
        )
    }
}
