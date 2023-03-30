package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.KrAppBootContext

interface EntitySinkFactory {
    fun KrAppBootContext.create(): EntitySink
}
