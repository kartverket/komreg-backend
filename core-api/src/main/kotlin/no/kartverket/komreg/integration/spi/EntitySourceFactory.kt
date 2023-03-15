package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.KrAppBootContext

interface EntitySourceFactory {
    fun KrAppBootContext.create(): EntitySource
}
