package no.kartverket.komreg.plugins.matrikkel

import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.integration.spi.IdTypeProvider
import no.statkart.matrikkel.komreg.getFylkeIdentType
import no.statkart.matrikkel.komreg.getKommuneIdentType
import no.statkart.matrikkel.komreg.model.Matrikkeltyper
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatrikkelIdTypeProviderTest {
    private val provider = MatrikkelIdTypeProvider()

    @Test
    fun `fylke og kommune gir riktig IdType`() = runBlocking {
        assertEquals(Matrikkeltyper.Fylke, provider.idTypeFor(getFylkeIdentType()))
        assertEquals(Matrikkeltyper.Kommune, provider.idTypeFor(getKommuneIdentType()))
    }

    @Test
    fun `ukjent identtype gir null`() = runBlocking {
        assertNull(provider.idTypeFor(no.kartverket.komreg.integration.spi.identTypeOf3<
            no.kartverket.komreg.core.domain.Fylkesnummer,
            no.kartverket.komreg.core.domain.Kommunenummer.Lopenummer,
            no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer>()))
    }

    @Test
    fun `ServiceLoader finner provideren`() {
        assertTrue(ServiceLoader.load(IdTypeProvider::class.java).any { it is MatrikkelIdTypeProvider })
    }
}
