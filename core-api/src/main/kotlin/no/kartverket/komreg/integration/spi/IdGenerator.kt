package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.KjoringContext
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType
import java.util.ServiceLoader

interface IdGenerator {
    fun generateId(hint: Any?): Id
}

interface IdGeneratorFactory {
    fun createFor(context: KjoringContext, idType: IdType<*, *>): IdGenerator?
}

class IdGeneratorManager(private val context: KjoringContext) {
    private val idGeneratorFactories: List<IdGeneratorFactory> = ServiceLoader.load(IdGeneratorFactory::class.java)
        .toList()

    fun idFor(idType: IdType<*, *>, hint: Any?): Id {
        return idGeneratorFactories.map { it.createFor(context, idType) }
            .filterNotNull()
            .single()
            .generateId(hint)
    }
}
