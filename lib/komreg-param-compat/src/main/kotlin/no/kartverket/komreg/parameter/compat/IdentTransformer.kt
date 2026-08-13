package no.kartverket.komreg.parameter.compat

import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Transformation

interface IdentTransformer {
    suspend fun transform(
        entity: Entity,
        idProvider: suspend (IdType<*, *>, Any?) -> Id,
    ): List<Transformation>?
}