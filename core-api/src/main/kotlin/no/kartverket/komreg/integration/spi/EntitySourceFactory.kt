package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.KjoringContext

interface EntitySourceFactory {
    fun KjoringContext.create(): EntitySource
}
