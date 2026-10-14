package no.kartverket.komreg.core

import no.kartverket.komreg.integration.spi.IdGeneratorManager

interface KjoringContext : KrAppBootContext {
    val kjoringId: Int
    val idGenerators: IdGeneratorManager
}
