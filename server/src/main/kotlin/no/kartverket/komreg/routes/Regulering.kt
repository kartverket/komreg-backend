package no.kartverket.komreg.routes

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.transformation.*
import java.util.Base64

@Serializable
data class Regulering(
    val id: String,
    val navn: String,
    val dato: LocalDate,
    val endringer: List<EndringDTO>,
) {
    fun toReguleringsinput(): Reguleringsinput {
        return Reguleringsinput(
            id,
            dato,
            endringer =
                endringer.flatMap { endring ->
                    endring.transformasjoner.map { transformasjon ->
                        when (transformasjon) {
                            is FylkeTransformasjonDTO -> {
                                Fylkeendring(
                                    fylkesnummer =
                                        FraEnTilMange(
                                            fra = Fylkesnummer(transformasjon.fylkesnummer.fra.toLong()),
                                            til = transformasjon.fylkesnummer.til.map { Fylkesnummer(it.toLong()) },
                                        ),
                                )
                            }

                            is KommuneTransformasjonDTO -> {
                                Kommuneendring(
                                    fylkesnummer =
                                        FraEnTilMange(
                                            fra = Fylkesnummer(transformasjon.fylkesnummer.fra.toLong()),
                                            til = transformasjon.fylkesnummer.til.map { Fylkesnummer(it.toLong()) },
                                        ),
                                    kommuneløpenummer =
                                        FraEnTilMange(
                                            fra = Kommunenummer.Lopenummer(transformasjon.kommuneløpenummer.fra.toByte()),
                                            til = transformasjon.kommuneløpenummer.til.map { Kommunenummer.Lopenummer(it.toByte()) },
                                        ),
                                )
                            }

                            is MatrikkelenhetTransformasjonDTO -> {
                                Matrikkelenhetendring(
                                    fylkesnummer =
                                        FraTil(
                                            fra = Fylkesnummer(transformasjon.fylkesnummer.fra.toLong()),
                                            til = Fylkesnummer(transformasjon.fylkesnummer.til.toLong()),
                                        ),
                                    kommuneløpenummer =
                                        FraTil(
                                            fra = Kommunenummer.Lopenummer(transformasjon.kommuneløpenummer.fra.toByte()),
                                            til = Kommunenummer.Lopenummer(transformasjon.kommuneløpenummer.til.toByte()),
                                        ),
                                    gårdsnummer =
                                        FraTil(
                                            fra = Matrikkelnummer.Gardsnummer(transformasjon.gårdsnummer.fra.toInt()),
                                            til = Matrikkelnummer.Gardsnummer(transformasjon.gårdsnummer.til.toInt()),
                                        ),
                                )
                            }

                            is KretsTransformasjonDTO -> {
                                Kretsendring(
                                    fylkesnummer =
                                        FraTil(
                                            fra = Fylkesnummer(transformasjon.fylkesnummer.fra.toLong()),
                                            til = Fylkesnummer(transformasjon.fylkesnummer.til.toLong()),
                                        ),
                                    kommuneløpenummer =
                                        FraTil(
                                            fra = Kommunenummer.Lopenummer(transformasjon.kommuneløpenummer.fra.toByte()),
                                            til = Kommunenummer.Lopenummer(transformasjon.kommuneløpenummer.til.toByte()),
                                        ),
                                    kretsnummer =
                                        FraTil(
                                            fra = Kretsnummer(transformasjon.kretsnummer.fra.toLong()),
                                            til = Kretsnummer(transformasjon.kretsnummer.til.toLong()),
                                        ),
                                    kretstype =
                                        FraTil(
                                            fra = Kretstype(transformasjon.kretstype.fra),
                                            til = Kretstype(transformasjon.kretstype.til),
                                        ),
                                )
                            }

                            is VegTransformasjonDTO -> {
                                Vegendring(
                                    fylkesnummer =
                                        FraEnTilMange(
                                            fra = Fylkesnummer(transformasjon.fylkesnummer.fra.toLong()),
                                            til = transformasjon.fylkesnummer.til.map { Fylkesnummer(it.toLong()) },
                                        ),
                                    kommuneløpenummer =
                                        FraEnTilMange(
                                            fra = Kommunenummer.Lopenummer(transformasjon.kommuneløpenummer.fra.toByte()),
                                            til = transformasjon.kommuneløpenummer.til.map { Kommunenummer.Lopenummer(it.toByte()) },
                                        ),
                                    adressekode =
                                        FraEnTilMange(
                                            fra = Adressekode(transformasjon.adressekode.fra.toInt()),
                                            til = transformasjon.adressekode.til.map { Adressekode(it.toInt()) },
                                        ),
                                )
                            }

                            is TeigTransformasjonDTO -> {
                                Teigendring(
                                    fylkesnummer =
                                        FraTil(
                                            fra = Fylkesnummer(transformasjon.fylkesnummer.fra.toLong()),
                                            til = Fylkesnummer(transformasjon.fylkesnummer.til.toLong()),
                                        ),
                                    kommuneløpenummer =
                                        FraTil(
                                            fra = Kommunenummer.Lopenummer(transformasjon.kommuneløpenummer.fra.toByte()),
                                            til = Kommunenummer.Lopenummer(transformasjon.kommuneløpenummer.til.toByte()),
                                        ),
                                    teigId =
                                        FraTil(
                                            fra = TeigId(transformasjon.teigId.fra.toLong()),
                                            til = TeigId(transformasjon.teigId.til.toLong()),
                                        ),
                                )
                            }

                            is VegadresseTransformasjonDTO -> {
                                Vegadresseendring(
                                    fylkesnummer =
                                        FraTil(
                                            fra = Fylkesnummer(transformasjon.fylkesnummer.fra.toLong()),
                                            til = Fylkesnummer(transformasjon.fylkesnummer.til.toLong()),
                                        ),
                                    kommuneløpenummer =
                                        FraTil(
                                            fra = Kommunenummer.Lopenummer(transformasjon.kommuneløpenummer.fra.toByte()),
                                            til = Kommunenummer.Lopenummer(transformasjon.kommuneløpenummer.til.toByte()),
                                        ),
                                    adressekode =
                                        FraTil(
                                            fra = Adressekode(transformasjon.adressekode.fra.toInt()),
                                            til = Adressekode(transformasjon.adressekode.til.toInt()),
                                        ),
                                    adressenummer =
                                        FraTil(
                                            fra = Adressenummernummer(transformasjon.adressenummer.fra.toShort()),
                                            til = Adressenummernummer(transformasjon.adressenummer.til.toShort()),
                                        ),
                                )
                            }
                        }
                    }
                },
            fylker =
                endringer.flatMap { endring ->
                    endring.nyeFylker.map {
                        Fylke(
                            fylkesnummer = Fylkesnummer(it.fylkesnummer.toLong()),
                            fylkesnavn = Fylkesnavn(it.navn),
                            gyldigTilDato = null,
                        )
                    }
                },
            kommuner =
                endringer.flatMap { endring ->
                    endring.nyeKommuner.map { kommune ->
                        Kommune(
                            kommunenummer =
                                Kommunenummer(
                                    Fylkesnummer(kommune.fylkesnummer.toLong()),
                                    Kommunenummer.Lopenummer(kommune.kommunenummer.toByte()),
                                ),
                            kommunenavn = Kommunenavn(kommune.navn),
                            gyldigTilDato = null,
                            koordinatsystem = kommune.koordinatsystem,
                            senterpunkt =
                                Koordinat(
                                    x = kommune.senterpunkt.x,
                                    y = kommune.senterpunkt.y,
                                ),
                            nedsattKonsesjonsgrense = kommune.nedsattKonsesjonsgrense,
                            godkjenteGardsnumre = kommune.godkjenteGardsnumre.joinToString(",") { serie -> serie.join() },
                            adresse =
                                kommune.adresse?.let {
                                    Postadresse(
                                        adresselinje1 = it.adresselinje1,
                                        adresselinje2 = it.adresselinje2,
                                        postnummer = it.postnummer,
                                        poststed = it.poststed,
                                    )
                                },
                            standardRekvirent =
                                kommune.standardRekvirent?.let {
                                    StandardRekvirent(it.orgnummer, it.navn)
                                },
                            // TODO: Vil feile i sinken dersom kommunevåpen ikke er satt
                            kommunevapen = kommune.kommunevapen?.let { Base64.getDecoder().decode(kommune.kommunevapen) },
                        )
                    }
                },
        )
    }
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
) : TransformasjonDTO()

@Serializable
@SerialName("kommune")
data class KommuneTransformasjonDTO(
    val fylkesnummer: FraEnTilMangeDTO,
    val kommuneløpenummer: FraEnTilMangeDTO,
) : TransformasjonDTO()

@Serializable
@SerialName("matrikkelenhet")
data class MatrikkelenhetTransformasjonDTO(
    val fylkesnummer: FraTilDTO,
    val kommuneløpenummer: FraTilDTO,
    val gårdsnummer: FraTilDTO,
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
    val koordinatsystem: Koordinatsystem,
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
    val koordinatsystem: Koordinatsystem? = null,
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

fun godkjenteGardsnumreTilListe(godkjenteGardsnumre: String?): List<Gardsnummerserie> {
    if (godkjenteGardsnumre == null) return emptyList()

    val serier = godkjenteGardsnumre.split(',')
    if (serier.isEmpty()) return emptyList()

    val liste = mutableListOf<Gardsnummerserie>()
    for (serie in serier) {
        if (serie.isNotBlank()) {
            if (serie.contains('-')) {
                val (fra, til) = serie.split('-')
                liste.add(Gardsnummerserie(fra.toInt(), til.toInt()))
            } else {
                liste.add(Gardsnummerserie(serie.toInt(), serie.toInt()))
            }
        }
    }

    return liste
}

fun Gardsnummerserie.join(): String = if (this.fra == this.til) "${this.fra}" else "${this.fra}-${this.til}"
