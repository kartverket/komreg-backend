package regulering.parameterfil

import regulering.model.FraTil
import regulering.model.SheetData
import regulering.model.SheetType
import java.io.File

internal fun List<String>.getAndClean(indeks: Int): String? =
    getOrNull(indeks)
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("Ingen", ignoreCase = true) }

internal fun List<String>.fraOgTil(fraIndeks: Int, tilIndeks: Int) =
    FraTil(getAndClean(fraIndeks), getAndClean(tilIndeks))

internal fun finnCSVFilnavn(resourceFolder: String): List<String> {
    val classLoader = Thread.currentThread().contextClassLoader
    val url = classLoader.getResource(resourceFolder)
        ?: error("Fant ikke resource-mappe: $resourceFolder")

    return when (url.protocol) {
        "file" -> {
            File(url.toURI())
                .listFiles { f -> f.extension == "csv" }
                ?.map { it.name }
                ?: emptyList()
        }

        "jar" -> {
            val jarPath = url.path.substringBefore("!").removePrefix("file:")
            java.util.zip.ZipFile(jarPath).use { jar ->
                jar.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith("$resourceFolder/") && it.endsWith(".csv") }
                    .map { it.substringAfterLast("/") }
                    .toList()
            }
        }

        else -> error("Ukjent protokoll for resource-mappe $resourceFolder: ${url.protocol}")
    }
}

internal fun readResourceLines(resourcePath: String): List<String> {
    val classLoader = Thread.currentThread().contextClassLoader
    val stream = classLoader.getResourceAsStream(resourcePath)
        ?: error("Fant ikke resource: $resourcePath")
    return stream.bufferedReader(Charsets.UTF_8).readLines()
}

fun finnStartRadOgTittel(lines: List<String>, separator: String, csvPath: String): Pair<Int, String> {
    val startRadOgTittel = lines.withIndex().firstNotNullOfOrNull { (i, line) ->
        val celle = line.split(separator).getOrNull(1)?.trim() ?: ""
        if (SheetType.entries.any { it.tittel == celle }) (i + 2) to celle else null
    }
    return startRadOgTittel
        ?: error("Fant ingen kjent tittel i kolonne 2 for $csvPath. Kjente titler: ${SheetType.entries.map { it.tittel }}")
}

fun lesSheet(resourcePath: String, separator: String = ";"): SheetData {
    val lines = readResourceLines(resourcePath)
    val (startRad, tittel) = finnStartRadOgTittel(lines, separator, resourcePath)
    val sheetType = SheetType.entries.first { it.tittel == tittel }
    val headers = lines[startRad - 1].split(separator).map { it.trim() }
    val dataLines = lines.drop(startRad)
        .takeWhile { line -> line.split(separator).any { it.isNotBlank() } }
        .map { it.split(separator) }

    return SheetData(sheetType, headers, dataLines)
}

// Lagrer hvilken kolonne som inneholder hvilken data.
// CSV exporten var litt inkonsekvent på om den tok med tomme kolonner og
// uansett litt mer fleksibelt for fremtidige endringer med dette

internal data class VegKolonner(
    val dagensKommunenummer: Int,
    val nyttKommunenummer: Int,
    val dagensAdressekode: Int,
    val nyAdressekode: Int,
    val dagensAdressenavn: Int,
    val nyttAdressenavn: Int,
    val dagensAdressenummer: Int,
    val nyttAdressenummer: Int,
) {
    companion object {
        fun fra(headers: List<String>) = VegKolonner(
            dagensKommunenummer = headers.indexOf("Dagens kommunenummer"),
            nyttKommunenummer = headers.indexOf("Nytt kommunenummer"),
            dagensAdressekode = headers.indexOf("Dagens adressekode"),
            nyAdressekode = headers.indexOf("Ny adressekode"),
            dagensAdressenavn = headers.indexOf("Dagens adressenavn"),
            nyttAdressenavn = headers.indexOf("Nytt adressenavn"),
            dagensAdressenummer = headers.indexOf("(Dagens adressenummer)"),
            nyttAdressenummer = headers.indexOf("(Nytt adressenummer)"),
        )
    }
}

internal data class KretsKolonner(
    val kommunenummer: Int,
    val dagensKretsnummer: Int,
    val nyttKretsnummer: Int,
    val dagensKretstype: Int,
    val nyKretstype: Int,
) {
    companion object {
        fun fra(headers: List<String>) = KretsKolonner(
            kommunenummer = headers.indexOf("Kommunenummer"),
            dagensKretsnummer = headers.indexOf("Dagens nummer"),
            nyttKretsnummer = headers.indexOf("Nytt nummer"),
            dagensKretstype = headers.indexOf("Dagens navn"),
            nyKretstype = headers.indexOf("Nytt navn"),
        )
    }
}

internal data class TeigKolonner(
    val dagensKommunenummer: Int,
    val nyttKommunenummer: Int,
    val dagensTeigId: Int,
    val nyttTeigId: Int,
) {
    companion object {
        fun fra(headers: List<String>) = TeigKolonner(
            dagensKommunenummer = headers.indexOf("Dagens kommunenummer"),
            nyttKommunenummer = headers.indexOf("Nytt kommunenummer"),
            dagensTeigId = headers.indexOf("Dagens teigId"),
            nyttTeigId = headers.indexOf("Nytt teigId"),
        )
    }
}

internal data class MatrikkelKolonner(
    val dagensKommunenummer: Int,
    val nyttKommunenummer: Int,
    val dagensGardsnummer: Int,
    val nyttGardsnummer: Int,
    val dagensBreuksnummer: Int,
    val nyttBruksnummer: Int,
) {
    companion object {
        fun fra(headers: List<String>) = MatrikkelKolonner(
            dagensKommunenummer = headers.indexOf("Dagens kommunenummer"),
            nyttKommunenummer = headers.indexOf("Nytt kommunenummer"),
            dagensGardsnummer = headers.indexOf("Dagens gårdsnummer"),
            nyttGardsnummer = headers.indexOf("Nytt gårdsnummer"),
            dagensBreuksnummer = headers.indexOf("(Dagens bruksnummer)"),
            nyttBruksnummer = headers.indexOf("(Nytt bruksnummer)"),
        )
    }
}
