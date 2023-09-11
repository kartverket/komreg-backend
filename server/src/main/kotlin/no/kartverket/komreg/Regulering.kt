package no.kartverket.komreg

import kotlinx.datetime.LocalDate
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
    val endringer: List<Fylkesdeling>,
) {
    fun toReguleringsinput(): Reguleringsinput {
        return Reguleringsinput(
            id,
            dato,
            endringer = endringer.flatMap { fylkesdeling ->
                fylkesdeling.nyeFylker.flatMap { fylke ->
                    fylke.kommuner.map { kommune ->
                        Kommuneendring(
                            Kommunenummer(kommune.kommunenummer.toLong()),
                            Kommunenummer(kommune.nyttKommunenummer.toLong()),
                        )
                    }
                }
            },
            fylker = endringer.flatMap { fylkesdeling ->
                fylkesdeling.nyeFylker.filter { it.skalOpprettes == true }.map {
                    Fylke(
                        Fylkesnummer(it.fylkesnummer.toLong()),
                        Fylkesnavn(it.navn),
                        null, // TODO: Bør bruke en annen type
                    )
                }
            },
            kommuner = endringer.flatMap { fylkesdeling ->
                fylkesdeling.nyeFylker.flatMap { fylke ->
                    fylke.kommuner.filter { nyKommune -> nyKommune.skalOpprettes == true }.map { nyKommune ->
                        Kommune(
                            kommunenummer = Kommunenummer(nyKommune.nyttKommunenummer.toLong()),
                            kommunenavn = Kommunenavn(nyKommune.navn),
                            gyldigTilDato = null,
                            koordinatsystem = nyKommune.koordinatsystem,
                            senterpunkt = Koordinat(
                                x = nyKommune.senterpunkt.x,
                                y = nyKommune.senterpunkt.y,
                            ),
                            nedsattKonsesjonsgrense = nyKommune.nedsattKonsesjonsgrense,
                            godkjenteGardsnumre = nyKommune.godkjenteGardsnumre.joinToString(",") { serie -> serie.join() },
                            adresse = nyKommune.adresse?.let {
                                Postadresse(
                                    adresselinje1 = it.adresselinje1,
                                    adresselinje2 = it.adresselinje2,
                                    postnummer = it.postnummer,
                                    poststed = it.poststed,
                                )
                            },
                            standardRekvirent = nyKommune.standardRekvirent?.let {
                                StandardRekvirent(it.orgnummer, it.navn)
                            },
                            // TODO: Vil feile dersom kommunevåpen ikke er satt
                            kommunevapen = Base64.getDecoder().decode(nyKommune.kommunevapen),
                        )
                    }
                }
            },
        )
    }
}

@Serializable
data class Fylkesdeling(
    val id: String,
    val navn: String,
    val type: String,
    val gammeltFylke: FylkeDTO,
    val nyeFylker: List<NyttFylke>,
)

@Serializable
data class FylkeDTO(
    val navn: String,
    val fylkesnummer: String,
)

@Serializable
data class NyttFylke(
    val navn: String,
    val fylkesnummer: String,
    val kommuner: List<NyKommune>,
    val skalOpprettes: Boolean? = false,
)

@Serializable
data class KommuneDTO(
    val navn: String,
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
data class NyKommune(
    val navn: String,
    val kommunenummer: String,
    val gyldigTilDato: LocalDate?,
    val koordinatsystem: Koordinatsystem,
    val senterpunkt: KoordinatDTO,
    val nedsattKonsesjonsgrense: Boolean,
    val godkjenteGardsnumre: List<Gardsnummerserie>,
    val adresse: AdresseDTO?,
    val standardRekvirent: StandardRekvirentDTO?,
    val kommunevapen: String?,
    val nyttKommunenummer: String,
    val skalOpprettes: Boolean? = false,
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
