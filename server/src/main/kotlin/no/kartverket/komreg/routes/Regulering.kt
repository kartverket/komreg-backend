package no.kartverket.komreg.routes

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.transformation.Kommuneendring
import no.kartverket.komreg.transformation.Reguleringsinput
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
            endringer = endringer.flatMap { endring ->
                endring.transformasjoner.mapNotNull {
                    when {
                        it is KommuneTransformasjonDTO -> {
                            Kommuneendring(
                                fra = Kommunenummer((it.fylkesnummer.fra + it.kommunenummer.fra).toLong()),
                                til = Kommunenummer((it.fylkesnummer.til + it.kommunenummer.til).toLong())
                            )
                        }

                        else -> {
                            null
                        }
                    }
                }
            },
            fylker = endringer.flatMap { endring ->
                endring.nyeFylker.map {
                    Fylke(
                        fylkesnummer = Fylkesnummer(it.fylkesnummer.toLong()),
                        fylkesnavn = Fylkesnavn(it.navn),
                        gyldigTilDato = null,
                    )
                }
            },
            kommuner = endringer.flatMap { endring ->
                endring.nyeKommuner.map { kommune ->
                    Kommune(
                        kommunenummer = Kommunenummer(
                            Fylkesnummer(kommune.fylkesnummer.toLong()),
                            Kommunenummer.Lopenummer(kommune.kommunenummer.toByte())
                        ),
                        kommunenavn = Kommunenavn(kommune.navn),
                        gyldigTilDato = null,
                        koordinatsystem = kommune.koordinatsystem,
                        senterpunkt = Koordinat(
                            x = kommune.senterpunkt.x,
                            y = kommune.senterpunkt.y,
                        ),
                        nedsattKonsesjonsgrense = kommune.nedsattKonsesjonsgrense,
                        godkjenteGardsnumre = kommune.godkjenteGardsnumre.joinToString(",") { serie -> serie.join() },
                        adresse = kommune.adresse?.let {
                            Postadresse(
                                adresselinje1 = it.adresselinje1,
                                adresselinje2 = it.adresselinje2,
                                postnummer = it.postnummer,
                                poststed = it.poststed,
                            )
                        },
                        standardRekvirent = kommune.standardRekvirent?.let {
                            StandardRekvirent(it.orgnummer, it.navn)
                        },
                        // TODO: Vil feile i sinken dersom kommunevåpen ikke er satt
                        kommunevapen = kommune.kommunevapen?.let { Base64.getDecoder().decode(kommune.kommunevapen) }
                    )
                }

            }
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
    val transformasjoner: List<TransformasjonDTO>
)

@Serializable
data class FraTil(
    val fra: String,
    val til: String?
)

@Serializable
sealed class TransformasjonDTO {
    abstract val fylkesnummer: FraTil
}

@Serializable
@SerialName("fylke")
data class FylkeTransformasjonDTO(
    override val fylkesnummer: FraTil
) : TransformasjonDTO()

@Serializable
@SerialName("kommune")
data class KommuneTransformasjonDTO(
    override val fylkesnummer: FraTil,
    val kommunenummer: FraTil
) : TransformasjonDTO()

@Serializable
data class FylkeDTO(
    val navn: String,
    val fylkesnummer: String,
)

@Serializable
data class EnkelKommuneDTO(
    val navn: String,
    val fylkesnummer: String,
    val kommunenummer: String
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
