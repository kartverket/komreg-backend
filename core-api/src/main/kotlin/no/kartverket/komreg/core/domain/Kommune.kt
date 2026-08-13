package no.kartverket.komreg.core.domain

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.kartverket.komreg.integration.spi.Ident2
import no.kartverket.komreg.integration.spi.IdentType2

@Serializable
data class Kommune(
    val kommunenummer: Kommunenummer,
    val kommunenavn: Kommunenavn,
    val gyldigTilDato: LocalDate?,
    val koordinatsystem: Koordinatsystem,
    val senterpunkt: Koordinat,
    val nedsattKonsesjonsgrense: Boolean,
    val godkjenteGardsnumre: String,
    val adresse: Postadresse?,
    val standardRekvirent: StandardRekvirent?,
    val kommunevapen: ByteArray,
) {
    companion object {
        val KommuneIdent : IdentType2<Fylkesnummer, Kommunenummer.Lopenummer> = runBlocking {
            Fylke.Ident.append<Kommunenummer.Lopenummer>()
        }

        operator fun invoke(
            kommunenummer: Long,
            kommunenavn: String,
            gyldigTilDato: LocalDate?,
            koordinatsystem: Koordinatsystem,
            senterpunkt: Koordinat,
            nedsattKonsesjonsgrense: Boolean,
            godkjenteGardsnumre: String,
            adresse: Postadresse?,
            standardRekvirent: StandardRekvirent?,
            kommunevapen: ByteArray,
        ): Kommune =
            Kommune(
                Kommunenummer(kommunenummer),
                Kommunenavn(kommunenavn),
                gyldigTilDato,
                koordinatsystem,
                senterpunkt,
                nedsattKonsesjonsgrense,
                godkjenteGardsnumre,
                adresse,
                standardRekvirent,
                kommunevapen,
            )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Kommune

        if (kommunenummer != other.kommunenummer) return false
        if (kommunenavn != other.kommunenavn) return false
        if (gyldigTilDato != other.gyldigTilDato) return false

        return true
    }

    override fun hashCode(): Int {
        var result = kommunenummer.hashCode()
        result = 31 * result + kommunenavn.hashCode()
        result = 31 * result + (gyldigTilDato?.hashCode() ?: 0)
        return result
    }
}

typealias KommuneIdent = Ident2<Fylkesnummer, Kommunenummer.Lopenummer>

@Serializable
data class Postadresse(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val adresselinje3: String? = null,
    val postnummer: String,
    val poststed: String,
)

@Serializable
data class StandardRekvirent(
    val orgnummer: String,
    val navn: String,
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

operator fun FylkeIdent.div(kommuneIdent: Kommunenummer.Lopenummer): KommuneIdent =
    appendWith(Kommune.KommuneIdent, kommuneIdent)
