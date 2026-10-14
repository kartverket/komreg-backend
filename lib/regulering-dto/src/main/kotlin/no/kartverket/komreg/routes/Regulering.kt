package no.kartverket.komreg.routes

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Regulering(
    val id: String,
    val navn: String,
    val dato: LocalDate,
    val endringer: List<EndringDTO>,
) {
}

@Serializable
data class EndringDTO(
    val id: String,
    val navn: String,
    val type: String,
    val utgåendeFylker: List<FylkeDTO>,
    val utgåendeKommuner: List<EnkelKommuneDTO>,
    val nyeFylker: List<FylkeDTO>,
    val nyeKommuner: List<KommuneDTO>,
    val transformasjoner: List<TransformasjonDTO>,
)

@Serializable
sealed class TransformasjonDTO

@Serializable
@SerialName("fylke")
data class FylkeTransformasjonDTO(
    val fylkesnummer: FraEnTilMangeDTO,
    val sammenslaa: Boolean? = false,
) : TransformasjonDTO()

@Serializable
@SerialName("kommune")
data class KommuneTransformasjonDTO(
    val fylkesnummer: FraEnTilMangeDTO,
    val kommuneløpenummer: FraEnTilMangeDTO,
    val sammenslaa: Boolean? = false,
) : TransformasjonDTO()

@Serializable
@SerialName("matrikkelenhet")
data class MatrikkelenhetTransformasjonDTO(
    val fylkesnummer: FraTilDTO,
    val kommuneløpenummer: FraTilDTO,
    val gårdsnummer: FraTilDTO,
    val bruksnummer: Map<Short, TilGrunneiendomDTO>? = null,
) : TransformasjonDTO()

@Serializable
@SerialName("krets")
data class KretsTransformasjonDTO(
    val fylkesnummer: FraTilDTO,
    val kommuneløpenummer: FraTilDTO,
    val kretsnummer: FraTilDTO,
    val kretstype: FraTilDTO,
) : TransformasjonDTO()

@Serializable
@SerialName("veg")
data class VegTransformasjonDTO(
    val fylkesnummer: FraEnTilMangeDTO,
    val kommuneløpenummer: FraEnTilMangeDTO,
    val adressekode: FraEnTilMangeDTO,
) : TransformasjonDTO()

@Serializable
@SerialName("teig")
data class TeigTransformasjonDTO(
    val fylkesnummer: FraTilDTO,
    val kommuneløpenummer: FraTilDTO,
    val teigId: FraTilDTO,
) : TransformasjonDTO()

@Serializable
@SerialName("vegadresse")
data class VegadresseTransformasjonDTO(
    val fylkesnummer: FraTilDTO,
    val kommuneløpenummer: FraTilDTO,
    val adressekode: FraTilDTO,
    val adressenummer: FraTilDTO,
) : TransformasjonDTO()

@Serializable
data class FraTilDTO(
    val fra: String,
    val til: String,
)

@Serializable
data class FraEnTilMangeDTO(
    val fra: String,
    val til: List<String>,
)

@Serializable
data class FylkeDTO(
    val navn: String,
    val fylkesnummer: String,
)

@Serializable
data class EnkelKommuneDTO(
    val navn: String,
    val fylkesnummer: String,
    val kommunenummer: String,
)

@Serializable
data class KommuneDTO(
    val navn: String,
    val fylkesnummer: String,
    val kommunenummer: String,
    val gyldigTilDato: LocalDate?,
    val koordinatsystem: KoordinatsystemDTO,
    val senterpunkt: KoordinatDTO,
    val nedsattKonsesjonsgrense: Boolean,
    val godkjenteGardsnumre: List<Gardsnummerserie>,
    val adresse: AdresseDTO?,
    val standardRekvirent: StandardRekvirentDTO?,
    val kommunevapen: String?,
)

@Serializable
data class OppdaterKommuneDTO(
    val navn: String? = null,
    val fylkesnummer: String,
    val kommunenummer: String,
    val gyldigTilDato: LocalDate? = null,
    val koordinatsystem: KoordinatsystemDTO? = null,
    val senterpunkt: KoordinatDTO? = null,
    val nedsattKonsesjonsgrense: Boolean? = null,
    val godkjenteGardsnumre: List<Gardsnummerserie>? = null,
    val adresse: AdresseDTO? = null,
    val standardRekvirent: StandardRekvirentDTO? = null,
    val kommunevapen: String? = null,
)

@Serializable
data class KoordinatDTO(
    val x: Double,
    val y: Double,
)

@Serializable
data class Gardsnummerserie(
    val fra: Int,
    val til: Int,
)

@Serializable
data class AdresseDTO(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val postnummer: String,
    val poststed: String,
)

@Serializable
data class StandardRekvirentDTO(
    val orgnummer: String,
    val navn: String,
)

@Serializable
data class TilGrunneiendomDTO(
    val fylkesnummer: Long? = null,
    val kommunelopenummer: Byte? = null,
    val gardsnummer: Int? = null,
    val bruksnummer: Short,
)

/**
 * Koordinatsystemer som brukes i Norge. Må kunne mappes til Koordinatsystem i core-api.
 */
@Serializable
enum class KoordinatsystemDTO {
    UTM32,
    UTM33,
    UTM35,
    UKJENT
}
