package no.kartverket.komreg.transformation

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.core.domain.PostadresseForOppretting
import no.kartverket.komreg.integration.spi.Ident1
import no.kartverket.komreg.integration.spi.Ident2
import no.kartverket.komreg.integration.spi.Ident3
import no.kartverket.komreg.integration.spi.Ident4
import no.kartverket.komreg.integration.spi.IdentType3
import no.kartverket.komreg.integration.spi.IdentType4
import no.kartverket.komreg.integration.spi.dropLast

fun Fylke.tilFylkesdata(): Fylkesdata =
    Fylkesdata(
        navn = fylkesnavn.name,
    )

fun Kommune.tilKommunedata(ikrafttredelsesdato: LocalDate): Kommunedata =
    Kommunedata(
        navn = kommunenavn.name.uppercase(),
        koordinatsystem = koordinatsystem,
        senterpunkt = senterpunkt,
        nedsattKonsesjonsgrense = nedsattKonsesjonsgrense,
        godkjenteGardsnumre = godkjenteGardsnumre,
        adresse = adresse?.let {
            PostadresseForOppretting(
                adresselinje1 = it.adresselinje1?.trim()?.ifEmpty { null },
                adresselinje2 = it.adresselinje2?.trim()?.ifEmpty { null },
                postnummer = it.postnummer,
            )
        },
        standardRekvirentOrgnummer = standardRekvirent?.orgnummer,
        kommunevapen = kommunevapen,
        ikrafttredelsesdato = ikrafttredelsesdato,
    )

object Matrikkelenhet {
    val GrunneiendomIdent: IdentType4<Fylkesnummer, Kommunenummer.Lopenummer, Matrikkelnummer.Gardsnummer, Matrikkelnummer.Bruksnummer> =
        runBlocking {
            Kommune.KommuneIdent.append<Matrikkelnummer.Gardsnummer>().append<Matrikkelnummer.Bruksnummer>()
        }

    val GardsnummerserieIdent: IdentType3<Fylkesnummer, Kommunenummer.Lopenummer, Matrikkelnummer.Gardsnummer> = GrunneiendomIdent.dropLast()
}
typealias FylkeIdent = Ident1<Fylkesnummer>
typealias KommuneIdent = Ident2<Fylkesnummer, Kommunenummer.Lopenummer>
typealias GrunneiendomIdent = Ident4<Fylkesnummer, Kommunenummer.Lopenummer, Matrikkelnummer.Gardsnummer, Matrikkelnummer.Bruksnummer>
typealias GardsnummerserieIdent = Ident3<Fylkesnummer, Kommunenummer.Lopenummer, Matrikkelnummer.Gardsnummer>

operator fun Ident2<Fylkesnummer, Kommunenummer.Lopenummer>.div(gardsnummer: Matrikkelnummer.Gardsnummer): GardsnummerserieIdent =
    appendWith(Matrikkelenhet.GardsnummerserieIdent, gardsnummer)

operator fun GardsnummerserieIdent.div(bruksnummer: Matrikkelnummer.Bruksnummer): GrunneiendomIdent =
    appendWith(Matrikkelenhet.GrunneiendomIdent, bruksnummer)