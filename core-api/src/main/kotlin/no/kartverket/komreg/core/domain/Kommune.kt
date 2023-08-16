package no.kartverket.komreg.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

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
    val kommunevapen: ByteArray?, // TODO: Tilbakeføring har ingen håndtering av at denne kan være null. Sette påkrevd?
) {
    companion object {
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
            kommunevapen: ByteArray?,
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

@Serializable
data class Koordinat(
    val x: Double,
    val y: Double,
)

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

@Serializable
enum class Koordinatsystem {
    UTM32,
    UTM33,
    UTM35,
    UKJENT,
}
