package no.kartverket.komreg.routes

import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Fylkesnavn
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunenavn
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Koordinat
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.core.domain.Kretsnummer
import no.kartverket.komreg.core.domain.Kretstype
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.core.domain.Postadresse
import no.kartverket.komreg.core.domain.StandardRekvirent
import no.kartverket.komreg.core.domain.TeigId
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.transformation.FraEnTilMange
import no.kartverket.komreg.transformation.FraTil
import no.kartverket.komreg.transformation.Fylkeendring
import no.kartverket.komreg.transformation.GrunneiendomIdent
import no.kartverket.komreg.transformation.Kommuneendring
import no.kartverket.komreg.transformation.Kretsendring
import no.kartverket.komreg.transformation.Matrikkelenhet
import no.kartverket.komreg.transformation.Matrikkelenhetendring
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.Teigendring
import no.kartverket.komreg.transformation.Vegadresseendring
import no.kartverket.komreg.transformation.Vegendring
import java.util.Base64

fun Regulering.toReguleringsinput(): Reguleringsinput {
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
                                    sammenslaaing = transformasjon.sammenslaa,
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
                                    sammenslaaing = transformasjon.sammenslaa,
                                )
                            }

                            is MatrikkelenhetTransformasjonDTO -> {
                                val tilFylkesnummer = Fylkesnummer(transformasjon.fylkesnummer.til.toLong())
                                val tilKommunenummer =
                                    Kommunenummer.Lopenummer(transformasjon.kommuneløpenummer.til.toByte())
                                val tilGardsnummer = transformasjon.gårdsnummer.til
                                    .toInt()
                                    .takeIf { it > 0 }
                                    ?.let(Matrikkelnummer::Gardsnummer)

                                val tilBruksnummer: Map<Matrikkelnummer.Bruksnummer, GrunneiendomIdent> =
                                    (transformasjon.bruksnummer ?: emptyMap()).entries.associate { (fraBruksnr, dto) ->
                                        Matrikkelnummer.Bruksnummer(fraBruksnr) to Matrikkelenhet.GrunneiendomIdent(
                                            dto.fylkesnummer?.let(::Fylkesnummer) ?: tilFylkesnummer,
                                            dto.kommunelopenummer?.let(Kommunenummer::Lopenummer) ?: tilKommunenummer,
                                            dto.gardsnummer?.let(Matrikkelnummer::Gardsnummer) ?: tilGardsnummer
                                            ?: throw IllegalArgumentException("Gårdsnummer må være satt"),
                                            Matrikkelnummer.Bruksnummer(dto.bruksnummer),
                                        )
                                    }
                                Matrikkelenhetendring(
                                    fylkesnummer = FraTil(
                                        fra = Fylkesnummer(transformasjon.fylkesnummer.fra.toLong()),
                                        til = tilFylkesnummer
                                    ),
                                    kommuneløpenummer = FraTil(
                                        fra = Kommunenummer.Lopenummer(transformasjon.kommuneløpenummer.fra.toByte()),
                                        til = tilKommunenummer,
                                    ),
                                    fraGardsnummer = Matrikkelnummer.Gardsnummer(transformasjon.gårdsnummer.fra.toInt()),
                                    tilGardsnummer = tilGardsnummer,
                                    bruksnummer = tilBruksnummer,
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
                            koordinatsystem = kommune.koordinatsystem.toKoordinatsystem(),
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
                            kommunevapen = kommune.kommunevapen.let {
                                Base64.getDecoder().decode(kommune.kommunevapen)
                            },
                        )
                    }
                },
        )
    }

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

fun KoordinatsystemDTO.toKoordinatsystem(): Koordinatsystem {
    return when (this) {
        KoordinatsystemDTO.UTM32 -> Koordinatsystem.UTM32
        KoordinatsystemDTO.UTM33 -> Koordinatsystem.UTM33
        KoordinatsystemDTO.UTM35 -> Koordinatsystem.UTM35
        KoordinatsystemDTO.UKJENT -> Koordinatsystem.UKJENT
    }
}
