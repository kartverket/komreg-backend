package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.KjoringContext

interface EntitySinkFactory {
    fun KjoringContext.create(): EntitySink
}
