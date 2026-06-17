package regulering.parameterfil

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ReguleringMetadata(
    val id:   String,
    val dato: LocalDate,
    val navn: String,
)

fun lesMetadata(resourceFolder: String): ReguleringMetadata {
    val resourcePath = "$resourceFolder/metadata.json"
    val json = readResourceLines(resourcePath).joinToString("\n")
    return try {
        Json.decodeFromString<ReguleringMetadata>(json)
    } catch (e: Exception) {
        error("Kunne ikke lese metadata fra $resourcePath: ${e.message}")
    }
}