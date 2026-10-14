package no.kartverket.komreg.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.kartverket.komreg.integration.spi.Payload

/**
 * Ekstra data for ny kommune.
 */
@Serializable
data class Kommunedata(
    val navn: String,
    val koordinatsystem: Koordinatsystem,
    val senterpunkt: Koordinat,
    val nedsattKonsesjonsgrense: Boolean,
    val godkjenteGardsnumre: String,
    val adresse: PostadresseForOppretting?,
    val standardRekvirentOrgnummer: String?,
    val ikrafttredelsesdato: LocalDate,
    val kommunevapen: ByteArray?,
) : Payload {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Kommunedata

        if (navn != other.navn) return false
        if (koordinatsystem != other.koordinatsystem) return false
        if (senterpunkt != other.senterpunkt) return false
        if (nedsattKonsesjonsgrense != other.nedsattKonsesjonsgrense) return false
        if (godkjenteGardsnumre != other.godkjenteGardsnumre) return false
        if (adresse != other.adresse) return false
        if (standardRekvirentOrgnummer != other.standardRekvirentOrgnummer) return false
        if (ikrafttredelsesdato != other.ikrafttredelsesdato) return false
        if (!kommunevapen.contentEquals(other.kommunevapen)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = navn.hashCode()
        result = 31 * result + (koordinatsystem.hashCode())
        result = 31 * result + senterpunkt.hashCode()
        result = 31 * result + nedsattKonsesjonsgrense.hashCode()
        result = 31 * result + godkjenteGardsnumre.hashCode()
        result = 31 * result + adresse.hashCode()
        result = 31 * result + standardRekvirentOrgnummer.hashCode()
        result = 31 * result + kommunevapen.contentHashCode()
        return result
    }
}

@Serializable
data class PostadresseForOppretting(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val postnummer: String,
)
