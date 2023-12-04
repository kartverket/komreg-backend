package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.KjoringContext
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType
import org.jetbrains.annotations.TestOnly
import java.util.ServiceLoader

interface IdGenerator {
    fun generateId(hint: Any?): Id
}

interface IdGeneratorFactory {
    fun createFor(context: KjoringContext, idType: IdType<*, *>): IdGenerator?
}

interface IdGeneratorManager {
    suspend fun idFor(idType: IdType<*, *>, hint: Any?): Id
}
