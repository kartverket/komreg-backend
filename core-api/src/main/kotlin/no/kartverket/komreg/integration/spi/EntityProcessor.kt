package no.kartverket.komreg.integration.spi

import kotlinx.coroutines.flow.Flow
import no.kartverket.komreg.core.KrAppBootContext

interface EntityProcessor {
    fun consume(t: Transformation)
    fun produce(): Flow<Transformation>
}

interface EntityProcessorFactory {
    fun KrAppBootContext.create(): EntityProcessor
}
