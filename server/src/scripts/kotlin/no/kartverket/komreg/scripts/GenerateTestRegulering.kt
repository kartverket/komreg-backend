package no.kartverket.komreg.scripts

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.routes.*
import org.slf4j.LoggerFactory
import org.slf4j.Logger

private enum class RegType {
    SAMMENSLAAING,
    GRENSEJUSTERING,
    SPLITTING,
}

val logger: Logger = LoggerFactory.getLogger(object {}::class.java)

fun main() {
    val env = dotenv {
        ignoreIfMissing = true
        systemProperties = true
    }

    val hikariConfig = HikariConfig().apply {
        poolName = "komreg-db-connection"
        jdbcUrl = env["DB_KOMREG_JDBC_URL"]
        username = env["DB_KOMREG_USERNAME"]
        password = env["DB_KOMREG_PASSWORD"]
        minimumIdle = 1
    }

    HikariDataSource(hikariConfig).use { pool ->
        val reguleringRepo = ReguleringRepo(pool)

        val regTypeToGenerate = try {
            RegType.valueOf(env["GENERATE_REG_TYPE"])
        } catch (e: Exception) {
            error("Invalid or missing GENERATE_REG_TYPE: ${e.message}")
        }
        val regulering = when (regTypeToGenerate) {
            RegType.SAMMENSLAAING -> createSammenslaaingRegulering()
            RegType.GRENSEJUSTERING -> createGrensejusteringRegulering()
            RegType.SPLITTING -> createSplittingRegulering()
        }

        try {
            reguleringRepo.insertRegulering(regulering)
            logger.info("${regulering.id} ble lagt inn i reguleringstabellen")
        } catch (e: Exception) {
            logger.info("Det skjedde en feil ved innsending av ${regulering.id}: ${e.message}")
        }
    }
}

private fun createSammenslaaingRegulering(): Regulering {
    val name = "Sammenslåing av Nittedal og Fredrikstad"
    val endring = EndringDTO(
        id = UUID.nameUUIDFromBytes(name.toByteArray()).toString().substring(0, 6),
        navn = name,
        type = "kommune",
        utgåendeFylker = listOf(),
        utgåendeKommuner = listOf(
            EnkelKommuneDTO(
                navn = "Nittedal",
                fylkesnummer = "1000002",
                kommunenummer = "01"
            ),
            EnkelKommuneDTO(
                navn = "Fredrikstad",
                fylkesnummer = "1000001",
                kommunenummer = "06"
            )
        ),
        nyeFylker = listOf(),
        nyeKommuner = listOf(
            KommuneDTO(
                navn = name,
                fylkesnummer = "99",
                kommunenummer = "90",
                gyldigTilDato = null,
                koordinatsystem = Koordinatsystem.UTM33,
                senterpunkt = KoordinatDTO(273342.7677, 6655574.2402),
                nedsattKonsesjonsgrense = false,
                godkjenteGardsnumre = listOf(
                    Gardsnummerserie(fra = 3, til = 4),
                ),
                adresse = AdresseDTO(
                    adresselinje1 = "Postboks 123",
                    adresselinje2 = "",
                    postnummer = "1350",
                    poststed = "LOMMEDALEN"
                ),
                standardRekvirent = StandardRekvirentDTO(
                    orgnummer = "773210000",
                    navn = "Panco AS"
                ),
                kommunevapen = createKommunevapenImage('K')
            )
        ),
        transformasjoner = listOf(
            KommuneTransformasjonDTO(
                fylkesnummer = FraEnTilMangeDTO(fra = "1000001", til = listOf("99")),
                kommuneløpenummer = FraEnTilMangeDTO(fra = "06", til = listOf("90")),
                sammenslaa = true
            ),
            KommuneTransformasjonDTO(
                fylkesnummer = FraEnTilMangeDTO(fra = "1000002", til = listOf("99")),
                kommuneløpenummer = FraEnTilMangeDTO(fra = "01", til = listOf("90"))
            ),
            MatrikkelenhetTransformasjonDTO(
                fylkesnummer = FraTilDTO("1000002", "99"),
                kommuneløpenummer = FraTilDTO("01", "90"),
                gårdsnummer = FraTilDTO("2", "3"),
                bruksnummer = null
            ),
            MatrikkelenhetTransformasjonDTO(
                fylkesnummer = FraTilDTO("1000001", "99"),
                kommuneløpenummer = FraTilDTO("06", "90"),
                gårdsnummer = FraTilDTO("426", "4"),
                bruksnummer = null
            ),
        )
    )

    return Regulering(
        id = "sammenslaa_test",
        navn = name,
        dato = LocalDate(2028, 1, 1),
        endringer = listOf(endring),
    )
}

private fun createGrensejusteringRegulering(): Regulering {
    val name = "Grensejustering mellom kommune X og Y"
    val endring = EndringDTO(
        id = UUID.nameUUIDFromBytes(name.toByteArray()).toString().substring(0, 6),
        navn = name,
        type = "kommune",
        utgåendeFylker = listOf(),
        utgåendeKommuner = listOf(),
        nyeFylker = listOf(),
        nyeKommuner = listOf(),
        transformasjoner = listOf()
    )
    return Regulering(
        id = "grensejustering",
        navn = name,
        dato = LocalDate(2028, 1, 1),
        endringer = listOf(endring),
    )
}

private fun createSplittingRegulering(): Regulering {
    val name = "Splitting av kommune X og Y"
    val endring = EndringDTO(
        id = UUID.nameUUIDFromBytes(name.toByteArray()).toString().substring(0, 6),
        navn = name,
        type = "kommune",
        utgåendeFylker = listOf(),
        utgåendeKommuner = listOf(),
        nyeFylker = listOf(),
        nyeKommuner = listOf(),
        transformasjoner = listOf()
    )
    return Regulering(
        id = "splitting",
        navn = name,
        dato = LocalDate(2028, 1, 1),
        endringer = listOf(endring),
    )
}

private fun createKommunevapenImage(char: Char): String {
    val width = 256
    val height = 256
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()

    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    graphics.color = Color.BLACK
    graphics.fillRect(0, 0, width, height)

    graphics.color = Color.WHITE
    graphics.font = Font("Arial", Font.BOLD, 180)
    val fontMetrics = graphics.fontMetrics
    val x = (width - fontMetrics.charWidth(char)) / 2
    val y = ((height - fontMetrics.height) / 2) + fontMetrics.ascent
    graphics.drawString("$char", x, y)

    graphics.dispose()

    val outputStream = ByteArrayOutputStream()
    ImageIO.write(image, "png", outputStream)
    return Base64.getEncoder().encodeToString(outputStream.toByteArray())
}
