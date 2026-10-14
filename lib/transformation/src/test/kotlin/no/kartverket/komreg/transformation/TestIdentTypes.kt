package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.*

typealias KommuneIdentType = IdentType2<Fylkesnummer, Kommunenummer.Lopenummer>

suspend fun getKommuneIdentType(): KommuneIdentType = identTypeOf2()


typealias GardsnummerIdentType = IdentType3<Fylkesnummer, Kommunenummer.Lopenummer, Matrikkelnummer.Gardsnummer>

typealias MatrikkelenhetIdentType = IdentType6<
    Fylkesnummer, Kommunenummer.Lopenummer,
    Matrikkelnummer.Gardsnummer, Matrikkelnummer.Bruksnummer, Matrikkelnummer.Festenummer, Matrikkelnummer.Seksjonsnummer
    >

suspend fun getMatrikkelenhetIdentType(): MatrikkelenhetIdentType = identTypeOf6()

typealias UnresolvedMatrikkelenhetIdentType = IdentType4<
    Matrikkelnummer.Gardsnummer, Matrikkelnummer.Bruksnummer, Matrikkelnummer.Festenummer, Matrikkelnummer.Seksjonsnummer
    >

suspend fun getUnresolvedMatrikkelenhetIdentType(): UnresolvedMatrikkelenhetIdentType = identTypeOf4()

typealias TeigIdentType = IdentType7<
    Fylkesnummer, Kommunenummer.Lopenummer,
    Matrikkelnummer.Gardsnummer, Matrikkelnummer.Bruksnummer, Matrikkelnummer.Festenummer, Matrikkelnummer.Seksjonsnummer,
    TeigId
    >

suspend fun getTeigIdentType(): TeigIdentType = identTypeOf7()


typealias BygningIdentType = IdentType3<Fylkesnummer, Kommunenummer.Lopenummer, Bygningsnummer>

suspend fun getBygningIdentType(): BygningIdentType = identTypeOf3()

typealias UnresolvedBygningIdentType = IdentType1<Bygningsnummer>

suspend fun getUnresolvedBygningIdentType(): UnresolvedBygningIdentType = identTypeOf1()


typealias AdresseparsellIdentType = IdentType3<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode>

suspend fun getAdresseparsellIdentType(): AdresseparsellIdentType = identTypeOf3()

typealias VegadresseIdentType = IdentType4<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode, Adressenummernummer> // Uten bokstav, da testene ikke trenger det

suspend fun getVegadresseIdentType(): VegadresseIdentType = identTypeOf4()

typealias UnresolvedVegadresseIdentType = IdentType1<Adressenummernummer> // Uten bokstav, da testene ikke trenger det

suspend fun getUnresolvedVegadresseIdentType(): UnresolvedVegadresseIdentType = identTypeOf1()


typealias KretsIdentType = IdentType4<Fylkesnummer, Kommunenummer.Lopenummer, Kretstype, Kretsnummer>

suspend fun getKretsIdentType(): KretsIdentType = identTypeOf4()
