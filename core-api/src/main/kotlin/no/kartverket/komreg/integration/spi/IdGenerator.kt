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

    suspend fun idsFor(idType: IdType<*, *>, hints: List<String?>): List<Pair<String?, Id>> {
        return hints.map { it to idFor(idType, it) }
    }

}

suspend fun <V : Any> IdGeneratorManager.idValueFor(idType: IdType<V, *>, hint: Any?): V {
    return idFor(idType, hint).typedValue(idType)!!
}

suspend fun <V : Any> IdGeneratorManager.idValuesFor(idType: IdType<V, *>, hints: List<String?>): List<Pair<String?, V>> {
    return idsFor(idType, hints).map { (hint, id) -> hint to id.typedValue(idType)!! }
}


