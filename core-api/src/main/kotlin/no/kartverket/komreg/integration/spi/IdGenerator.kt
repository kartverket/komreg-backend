package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.KjoringContext
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType

interface IdGenerator {
    fun generateId(hint: Any?): Id
}

interface IdGeneratorFactory {
    fun createFor(context: KjoringContext, idType: IdType<*, *>): IdGenerator?
}

interface IdGeneratorManager {
    suspend fun idFor(idType: IdType<*, *>, hint: Any?): Id

    suspend fun idFor(idType: IdType<*, *>, hint: List<Any?>): List<Id> {
        return hint.map { idFor(idType, it) }
    }

}

suspend fun <V : Any> IdGeneratorManager.idValueFor(idType: IdType<V, *>, hint: Any?): V {
    return idFor(idType, hint).typedValue(idType)!!
}
