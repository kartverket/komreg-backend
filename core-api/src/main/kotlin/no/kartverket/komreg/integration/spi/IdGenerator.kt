package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType
import java.util.ServiceLoader

interface IdGenerator {
    fun generateId(): Id
}

interface IdGeneratorFactory {
    fun createFor(context: KrAppBootContext, idType: IdType<*, *>): IdGenerator?
}

class IdGeneratorManager(private val context: KrAppBootContext) {
    private val idGeneratorFactories: List<IdGeneratorFactory> = ServiceLoader.load(IdGeneratorFactory::class.java)
        .toList()

    fun idFor(idType: IdType<*, *>): Id {
        return idGeneratorFactories.map { it.createFor(context, idType) }
            .filterNotNull()
            .single()
            .generateId()
    }
}
