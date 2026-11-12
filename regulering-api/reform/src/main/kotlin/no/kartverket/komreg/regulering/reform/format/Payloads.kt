package no.kartverket.komreg.regulering.reform.format

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Koordinat
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.core.domain.PostadresseForOppretting
import java.util.Base64

/** Payload for `county`. */
@Serializable
data class FylkePayload(val navn: String) {
    fun toFylkesdata(): Fylkesdata = Fylkesdata(navn = navn)
}

/**
 * Payload for `municipality`. Fylkes- og kommunenummer kommer fra nøkkelen, og ikrafttredelsesdato fra
 * [ReformSpec.ikrafttredelse].
 */
@Serializable
data class KommunePayload(
    val navn: String,
    val koordinatsystem: Koordinatsystem,
    val senterpunkt: Koordinat,
    val nedsattKonsesjonsgrense: Boolean = false,
    val godkjenteGardsnumre: List<Gardsnummerserie> = emptyList(),
    val adresse: Adresse? = null,
    val standardRekvirentOrgnummer: String? = null,
    /** Base64, som i `KommuneDTO`. */
    val kommunevapen: String? = null,
) {
    @Serializable
    data class Gardsnummerserie(val fra: Int, val til: Int)

    @Serializable
    data class Adresse(
        val adresselinje1: String? = null,
        val adresselinje2: String? = null,
        val postnummer: String,
    )

    /** Samme normalisering som `Kommune.tilKommunedata` (navn med store bokstaver, tomme adresselinjer fjernes). */
    fun toKommunedata(ikrafttredelse: LocalDate): Either<String, Kommunedata> = either {
        godkjenteGardsnumre.forEach {
            ensure(it.fra in 1..it.til) { "Ugyldig gårdsnummerserie ${it.fra}-${it.til}" }
        }
        val vapen = kommunevapen?.let {
            Either.catchOrThrow<IllegalArgumentException, ByteArray> { Base64.getDecoder().decode(it) }
                .mapLeft { "Kommunevåpenet er ikke gyldig base64" }
                .bind()
        }
        Kommunedata(
            navn = navn.uppercase(),
            koordinatsystem = koordinatsystem,
            senterpunkt = senterpunkt,
            nedsattKonsesjonsgrense = nedsattKonsesjonsgrense,
            godkjenteGardsnumre = godkjenteGardsnumre.joinToString(",") {
                if (it.fra == it.til) "${it.fra}" else "${it.fra}-${it.til}"
            },
            adresse = adresse?.let {
                PostadresseForOppretting(
                    adresselinje1 = it.adresselinje1?.trim()?.ifEmpty { null },
                    adresselinje2 = it.adresselinje2?.trim()?.ifEmpty { null },
                    postnummer = it.postnummer,
                )
            },
            standardRekvirentOrgnummer = standardRekvirentOrgnummer,
            ikrafttredelsesdato = ikrafttredelse,
            kommunevapen = vapen,
        )
    }
}
