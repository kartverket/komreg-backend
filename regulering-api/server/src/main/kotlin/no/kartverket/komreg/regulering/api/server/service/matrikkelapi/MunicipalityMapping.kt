package no.kartverket.komreg.regulering.api.server.service.matrikkelapi

import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunenavn
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Koordinat
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.core.domain.Postadresse
import no.kartverket.komreg.core.domain.StandardRekvirent
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.adresse.Postnummeromrade
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.person.JuridiskPerson
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.Fylke as WsFylke
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.Kommune as WsKommune
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.LocalDate as WsLocalDate

/** Tolker feltene som `KommuneServiceImpl` i matrikkel-komreg gjør. */
internal fun WsFylke.toCore(): Fylke = Fylke(fylkesnummer.toLong(), fylkesnavn, gyldigTilDato?.toKotlin())

internal fun WsKommune.toCore(
    postnummeromrade: Postnummeromrade?,
    standardRekvirent: JuridiskPerson?,
    kommunevapen: ByteArray?,
): Kommune {
    val (county, serial) = kommunenummer.let { it.dropLast(2) to it.takeLast(2) }
    return Kommune(
        kommunenummer = Kommunenummer(
            Fylkesnummer(county.toLong()),
            Kommunenummer.Lopenummer(serial.toByte()),
        ),
        kommunenavn = Kommunenavn(kommunenavn),
        gyldigTilDato = gyldigTilDato?.toKotlin(),
        koordinatsystem = koordinatsystemFraKodeId(koordinatsystemKodeId?.value),
        senterpunkt = Koordinat(x = senterpunkt?.x ?: 0.0, y = senterpunkt?.y ?: 0.0),
        nedsattKonsesjonsgrense = isNedsattKonsesjonsgrense ?: false,
        godkjenteGardsnumre = brukteGardsnummer ?: "",
        adresse = postnummeromrade?.kretsnummer?.let { postnummer ->
            Postadresse(
                adresselinje1 = postadresse?.adresselinje1.blankToNull(),
                adresselinje2 = postadresse?.adresselinje2.blankToNull(),
                adresselinje3 = postadresse?.adresselinje3.blankToNull(),
                postnummer = postnummer.toString().padStart(4, '0'),
                poststed = postnummeromrade.kretsnavn,
            )
        },
        standardRekvirent = standardRekvirent?.nummer?.let { StandardRekvirent(orgnummer = it, navn = standardRekvirent.navn) },
        kommunevapen = kommunevapen ?: ByteArray(0),
    )
}

internal fun koordinatsystemFraKodeId(kodeId: Long?): Koordinatsystem = when (kodeId) {
    10L -> Koordinatsystem.UTM32
    11L -> Koordinatsystem.UTM33
    13L -> Koordinatsystem.UTM35
    else -> Koordinatsystem.UKJENT
}

private fun String?.blankToNull(): String? = this?.trim()?.ifEmpty { null }

private fun WsLocalDate.toKotlin(): LocalDate = LocalDate(date.year, date.month, date.day)
