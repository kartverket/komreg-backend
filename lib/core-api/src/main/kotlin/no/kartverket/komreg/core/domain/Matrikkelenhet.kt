package no.kartverket.komreg.core.domain

import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.core.domain.Matrikkelnummer.Bruksnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer
import no.kartverket.komreg.integration.spi.*

object Matrikkelenhet {
    val GrunneiendomIdent: IdentType4<Fylkesnummer, Kommunenummer.Lopenummer, Gardsnummer, Bruksnummer> = runBlocking {
        Kommune.KommuneIdent.append<Gardsnummer>().append<Bruksnummer>()
    }

    val GardsnummerserieIdent: IdentType3<Fylkesnummer, Kommunenummer.Lopenummer, Gardsnummer> = GrunneiendomIdent.dropLast()
}


typealias GrunneiendomIdent = Ident4<Fylkesnummer, Kommunenummer.Lopenummer, Gardsnummer, Bruksnummer>
typealias GardsnummerserieIdent = Ident3<Fylkesnummer, Kommunenummer.Lopenummer, Gardsnummer>

operator fun KommuneIdent.div(gardsnummer: Gardsnummer): GardsnummerserieIdent =
    appendWith(Matrikkelenhet.GardsnummerserieIdent, gardsnummer)

operator fun GardsnummerserieIdent.div(bruksnummer: Bruksnummer): GrunneiendomIdent =
    appendWith(Matrikkelenhet.GrunneiendomIdent, bruksnummer)

