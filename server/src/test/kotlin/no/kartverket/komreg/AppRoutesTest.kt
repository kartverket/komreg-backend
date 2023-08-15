package no.kartverket.komreg

import io.kotest.core.spec.style.AnnotationSpec
import kotlin.test.assertContains
import kotlin.test.assertEquals

class AppRoutesTest : AnnotationSpec() {

    @Test
    fun godkjenteGardsnumreFraStringTilListe() {
        val gardsnummerliste = godkjenteGardsnumreTilListe("1-100,200,300")
        assertEquals(3, gardsnummerliste.size)
        assertContains(gardsnummerliste, Gardsnummerserie(1, 100))
        assertContains(gardsnummerliste, Gardsnummerserie(200, 200))
        assertContains(gardsnummerliste, Gardsnummerserie(300, 300))
    }

    @Test
    fun godkjenteGardsnumreFraListeTilString() {
        val gardsnummerliste = listOf(Gardsnummerserie(1, 100), Gardsnummerserie(200, 200))
        val gardsnumre = gardsnummerliste.joinToString(",") { serie -> serie.join() }
        assertEquals("1-100,200", gardsnumre)
    }
}
