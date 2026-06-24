package regulering.parameterfil

import no.kartverket.komreg.routes.KretsTransformasjonDTO
import no.kartverket.komreg.routes.MatrikkelenhetTransformasjonDTO
import no.kartverket.komreg.routes.TeigTransformasjonDTO
import no.kartverket.komreg.routes.TilGrunneiendomDTO
import no.kartverket.komreg.routes.TransformasjonDTO
import no.kartverket.komreg.routes.VegTransformasjonDTO
import no.kartverket.komreg.routes.VegadresseTransformasjonDTO
import regulering.model.FraTil
import regulering.model.KretsRad
import regulering.model.MatrikkelRad
import regulering.model.TeigRad
import regulering.model.VegRad
import regulering.model.tilFraEnTilMangeDTO
import regulering.model.tilFraTilDTO

data class KommuneSplit(
    val fylkesnummer: String,
    val løpenummer: String
)

fun splitKommunenummer(nr: String): KommuneSplit {
    require(nr.length >= 3) { "Kommunenummer '$nr' er for kort til å splitte" }
    return KommuneSplit(
        fylkesnummer = nr.dropLast(2),
        løpenummer = nr.takeLast(2)
    )
}

private fun FraTil.tilFylkesnummerDTO(): FraTil =
    FraTil(
        fra = fra?.let { splitKommunenummer(it).fylkesnummer },
        til = til?.let { splitKommunenummer(it).fylkesnummer },
    )

private fun FraTil.tilLøpenummerDTO(): FraTil =
    FraTil(
        fra = fra?.let { splitKommunenummer(it).løpenummer },
        til = til?.let { splitKommunenummer(it).løpenummer },
    )

fun byggVegTransformasjoner(rad: VegRad): TransformasjonDTO {
    val fylke = rad.kommunenummer.tilFylkesnummerDTO()
    val løp = rad.kommunenummer.tilLøpenummerDTO()

    val harAdressenummer = rad.adressenummer.fra != null || rad.adressenummer.til != null

    val transformasjon = if(harAdressenummer){
        VegadresseTransformasjonDTO(
            fylkesnummer = fylke.tilFraTilDTO("fylkesnummer"),
            kommuneløpenummer = løp.tilFraTilDTO("kommuneløpenummer"),
            adressekode = rad.adressekode.tilFraTilDTO("adressekode"),
            adressenummer = rad.adressenummer.tilFraTilDTO("adressenummer"),
        )
    } else VegTransformasjonDTO(
        fylkesnummer = fylke.tilFraEnTilMangeDTO("fylkesnummer"),
        kommuneløpenummer = løp.tilFraEnTilMangeDTO("kommuneløpenummer"),
        adressekode = rad.adressekode.tilFraEnTilMangeDTO("adressekode"),
    )

    return transformasjon
}

fun byggKretsTransformasjon(rad: KretsRad): TransformasjonDTO {
    val fylke = rad.kommunenummer.tilFylkesnummerDTO()
    val løp = rad.kommunenummer.tilLøpenummerDTO()

    return KretsTransformasjonDTO(
        fylkesnummer = fylke.tilFraTilDTO("fylkesnummer"),
        kommuneløpenummer = løp.tilFraTilDTO("kommuneløpenummer"),
        kretsnummer = rad.kretsnummer.tilFraTilDTO("kretsnummer"),
        kretstype = rad.kretstype.tilFraTilDTO("kretstype"),
    )
}

fun byggTeigTransformasjon(rad: TeigRad): TransformasjonDTO {
    val fylke = rad.kommunenummer.tilFylkesnummerDTO()
    val løp = rad.kommunenummer.tilLøpenummerDTO()

    return TeigTransformasjonDTO(
        fylkesnummer = fylke.tilFraTilDTO("fylkesnummer"),
        kommuneløpenummer = løp.tilFraTilDTO("kommuneløpenummer"),
        teigId = rad.teigId.tilFraTilDTO("teigId"),
    )
}

private fun MatrikkelRad.tilBruksnummerMap(): Map<Short, TilGrunneiendomDTO> {
    val dagens = bruksnummer.fra ?: error("Mangler 'Dagens bruksnummer'")
    val nytt = bruksnummer.til ?: error("Mangler 'Nytt bruksnummer'")

    val dagensShort = dagens.toShortOrNull() ?: error("Ugyldig dagens bruksnummer: '$dagens'")
    val nyttShort = nytt.toShortOrNull() ?: error("Ugyldig nytt bruksnummer: '$nytt'")

    return mapOf(dagensShort to TilGrunneiendomDTO(bruksnummer = nyttShort))
}

fun byggMatrikkelTransformasjon(rad: MatrikkelRad): TransformasjonDTO {
    val fylke = rad.kommunenummer.tilFylkesnummerDTO()
    val løp = rad.kommunenummer.tilLøpenummerDTO()

    return MatrikkelenhetTransformasjonDTO(
        fylkesnummer = fylke.tilFraTilDTO("fylkesnummer"),
        kommuneløpenummer = løp.tilFraTilDTO("kommuneløpenummer"),
        gårdsnummer = rad.gardsnummer.tilFraTilDTO("gårdsnummer"),
        bruksnummer = rad.tilBruksnummerMap(),
    )
}