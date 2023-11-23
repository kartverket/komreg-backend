package no.kartverket.komreg.integration.spi

import kotlinx.coroutines.flow.Flow
import no.kartverket.komreg.core.KjoringContext

interface EntityProcessor {
    fun consume(t: Transformation)
    fun produce(): Flow<Transformation>
}

interface EntityProcessorFactory {
    fun KjoringContext.create(): EntityProcessor
}
