package no.kartverket.komreg.integration

import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.core.domain.IdType
import no.kartverket.komreg.integration.spi.IdTypeProvider
import no.kartverket.komreg.integration.spi.IdentOrEmptyType
import no.kartverket.komreg.integration.spi.identTypeOf1
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.statkart.matrikkel.komreg.model.Matrikkeltyper
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdTypeProviderManagerTest {
    private fun provider(result: IdType<*, *>?) = object : IdTypeProvider {
        override suspend fun idTypeFor(identType: IdentOrEmptyType<*>) = result
    }

    private val identType = runBlocking { identTypeOf1<Fylkesnummer>() }

    @Test
    fun `ingen provider gir null`() = runBlocking {
        assertNull(IdTypeProviderManager(emptyList()).idTypeFor(identType))
    }

    @Test
    fun `en provider gir svaret`() = runBlocking {
        val manager = IdTypeProviderManager(listOf(provider(null), provider(Matrikkeltyper.Fylke)))
        assertEquals(Matrikkeltyper.Fylke, manager.idTypeFor(identType))
    }

    @Test
    fun `ulike svar feiler`() {
        val manager = IdTypeProviderManager(listOf(provider(Matrikkeltyper.Fylke), provider(Matrikkeltyper.Kommune)))
        assertFailsWith<IllegalStateException> { runBlocking { manager.idTypeFor(identType) } }
    }

    @Test
    fun `ServiceLoader finner provider fra pluginmodulen`() {
        assertTrue(ServiceLoader.load(IdTypeProvider::class.java).toList().isNotEmpty())
    }
}
