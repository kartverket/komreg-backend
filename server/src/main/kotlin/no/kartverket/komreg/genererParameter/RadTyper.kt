package regulering.model

data class FraTil(
    val fra: String?,
    val til: String?
)

data class VegRad(
    val kommunenummer: FraTil,
    val adressekode: FraTil,
    val adressenavn: FraTil,
    val adressenummer: FraTil,
)

data class KretsRad(
    val kommunenummer: FraTil,
    val kretsnummer: FraTil,
    val kretstype: FraTil,
)

data class TeigRad(
    val kommunenummer: FraTil,
    val teigId: FraTil,
)

data class MatrikkelRad(
    val kommunenummer: FraTil,
    val gardsnummer: FraTil,
    val bruksnummer: FraTil,
)

data class SheetData(
    val type: SheetType,
    val headers: List<String>,
    val linjer: List<List<String>>,
)

fun FraTil.tilFraTilDTO(felt: String): no.kartverket.komreg.routes.FraTilDTO {
    val fraVerdi = fra ?: error("Mangler 'fra'-verdi for felt '$felt'")
    val tilVerdi = til ?: error("Mangler 'til'-verdi for felt '$felt'")
    return no.kartverket.komreg.routes.FraTilDTO(fra = fraVerdi, til = tilVerdi)
}

fun FraTil.tilFraEnTilMangeDTO(felt: String): no.kartverket.komreg.routes.FraEnTilMangeDTO {
    val fraVerdi = fra ?: error("Mangler 'fra'-verdi for felt '$felt'")
    val tilVerdi = til ?: error("Mangler 'til'-verdi for felt '$felt'")
    return no.kartverket.komreg.routes.FraEnTilMangeDTO(fra = fraVerdi, til = listOf(tilVerdi))
}