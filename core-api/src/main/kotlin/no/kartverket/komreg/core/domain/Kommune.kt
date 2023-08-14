package no.kartverket.komreg.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class Kommune(
    val kommunenummer: Kommunenummer,
    val kommunenavn: Kommunenavn,
    val gyldigTilDato: LocalDate?,
    val koordinatsystem: Koordinatsystem?,
    val senterpunkt: Senterpunkt,
    val nedsattKonsesjonsgrense: Boolean,
    val godkjenteGardsnumre: String,
    val adresse: Adresse,
    val standardRekvirent: StandardRekvirent,
    val kommunevapen: ByteArray?,
) {
    companion object {
        operator fun invoke(
            kommunenummer: Long,
            kommunenavn: String,
            gyldigTilDato: LocalDate?,
            koordinatsystem: Koordinatsystem,
            senterpunkt: Senterpunkt,
            nedsattKonsesjonsgrense: Boolean,
            godkjenteGardsnumre: String,
            adresse: Adresse,
            standardRekvirent: StandardRekvirent,
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
data class Senterpunkt(
    val x: Double,
    val y: Double,
)

@Serializable
data class Adresse(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val adresselinje3: String? = null,
    val postnummer: String?,
    val poststed: String?,
)

@Serializable
data class StandardRekvirent(
    val orgnummer: String?,
    val navn: String?,
)

@Serializable
enum class Koordinatsystem(val kodeId: Long) {
    UTM32(10),
    UTM33(11),
    UTM35(13),
    TESTKOORDINATSYSTEM(2), // Brukes av Testkommune 9999
    ;

    companion object {
        fun fraKode(kodeId: Long): Koordinatsystem? {
            return values().firstOrNull { it.kodeId == kodeId }
        }
    }
}
