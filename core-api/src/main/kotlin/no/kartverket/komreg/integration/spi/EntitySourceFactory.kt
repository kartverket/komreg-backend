package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.Product

interface EntitySourceFactory {
        fun KrAppBootContext.create(): EntitySource<Product<*>>
    }