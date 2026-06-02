package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.domain.*
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

fun dummyKommune(fylkesnummer: Int, lopenummer: Int): Kommune {
    return Kommune(
        kommunenummer = Kommunenummer(
            Fylkesnummer(fylkesnummer.toLong()),
            Kommunenummer.Lopenummer(lopenummer.toByte()),
        ),
        kommunenavn = Kommunenavn("Dummy"),
        koordinatsystem = Koordinatsystem.UTM32,
        senterpunkt = Koordinat(123.0, 456.0),
        nedsattKonsesjonsgrense = false,
        godkjenteGardsnumre = "1,2,3",
        gyldigTilDato = null,
        adresse = null,
        standardRekvirent = null,
        kommunevapen = createDummyKommunevaapenImage('K'),
    )
}

fun createDummyKommunevaapenImage(char: Char): ByteArray {
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
    return outputStream.toByteArray()
}
