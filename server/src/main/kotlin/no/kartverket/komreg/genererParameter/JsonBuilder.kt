package regulering.parameterfil

import regulering.model.FraTil
import regulering.model.Transformasjon
import java.time.LocalDateTime

// Dropper kanskje denne, men tar den med i draften uansett som referanse

private fun String?.jsonVerdi(): String = if (this == null) "\"\"" else "\"$this\""

private fun fratilJson(ft: FraTil): String =
    """{ "fra": ${ft.fra.jsonVerdi()}, "til": ${ft.til.jsonVerdi()} }"""

private fun transformasjonTilJson(t: Transformasjon): String = buildString {
    appendLine("    {")
    appendLine("      \"type\": \"${t.type}\",")

    val fields = listOfNotNull(
        t.fylkesnummer?.let { "\"fylkesnummer\": ${fratilJson(it)}" },
        t.kommuneløpenummer?.let { "\"kommuneløpenummer\": ${fratilJson(it)}" },
        t.adressekode?.let { "\"adressekode\": ${fratilJson(it)}" },
        t.adressenummer?.let { "\"adressenummer\": ${fratilJson(it)}" },
        t.kretsnummer?.let { "\"kretsnummer\": ${fratilJson(it)}" },
        t.kretstype?.let { "\"kretstype\": ${fratilJson(it)}" },
        t.teigId?.let { "\"teigId\": ${fratilJson(it)}" },
        t.gardsnummer?.let { "\"gardsnummer\": ${fratilJson(it)}" },
        t.bruksnummer?.let { "\"bruksnummer\": ${fratilJson(it)}" },
    )

    appendLine(fields.joinToString(",\n") { "      $it" })
    append("    }")
}

fun tilJson(
    transformasjoner: List<Transformasjon>,
    id: String,
    dato: String,
    navn: String,
): String {
    val transformasjonerJson = transformasjoner
        .joinToString(",\n") { transformasjonTilJson(it) }

    val timestamp = LocalDateTime.now()

    return """
{
  "id": "testing-$id-$timestamp",
  "dato": "$dato",
  "navn": "$navn",
  "endringer": [
    {
      "id": "$id",
      "navn": "$navn",
      "type": "kommune",
      "nyeFylker": [],
      "nyeKommuner": [],
      "utgåendeFylker": [],
      "utgåendeKommuner": [],
      "transformasjoner": [
$transformasjonerJson
      ]
    }
  ]
}
""".trimIndent()
}