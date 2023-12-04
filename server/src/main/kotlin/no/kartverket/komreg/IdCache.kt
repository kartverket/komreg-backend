package no.kartverket.komreg

import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.resourceScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.encodeToString
import no.kartverket.komreg.core.KjoringContext
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType
import no.kartverket.komreg.integration.spi.IdGenerator
import no.kartverket.komreg.integration.spi.IdGeneratorFactory
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import javax.sql.DataSource

class IdCache(
    private val kjoringContext: KjoringContext,
    private val dataSource: DataSource
) : IdGeneratorManager {
    companion object {
        private val json = jsonSerializer()
    }

    private inner class IdGeneratorWrapper<V : @Contextual Any, Self : @Contextual Any> (
        private val idType: IdType<V, Self>,
        private val idGenerator: IdGenerator)  {

        val idTypeJson = json.encodeToString(idType)

        suspend fun generateId(hint: Any?): Id {
            if (hint == null) {
                return idGenerator.generateId(null)
            }
            return withContext(Dispatchers.IO) {
                resourceScope {
                    val conn = autoCloseable { dataSource.connection }
                    conn.autoCommit = false
                    val stmt = autoCloseable {
                        conn.prepareStatement("SELECT id FROM id_cache WHERE cache_hint = ? AND id_type = ?::jsonb FOR UPDATE")
                    }
                    val rs = autoCloseable {
                        stmt.setString(1, hint.toString())
                        stmt.setString(2, idTypeJson)
                        stmt.executeQuery()
                    }
                    if (rs.next()) {
                        Id(idType, json.decodeFromString(idType.valueSerializer, rs.getString(1)))
                    } else {
                        idGenerator
                            .generateId(hint)
                            .also { id ->
                                val insertStmt = autoCloseable {
                                    conn.prepareStatement("INSERT INTO id_cache (cache_hint, id_type, id) VALUES (?, ?::jsonb, ?::jsonb)")
                                }
                                insertStmt.setString(1, hint.toString())
                                insertStmt.setString(2, idTypeJson)
                                insertStmt.setString(3,
                                    json.encodeToString(idType.valueSerializer, id.typedValue(idType)!!)
                                )
                                insertStmt.executeUpdate()
                            }
                    }.also {
                        conn.commit()
                    }
                }
            }
        }

    }

    private val idGeneratorFactories: ConcurrentMap<IdType<*, *>, IdGeneratorWrapper<*, *>> = ConcurrentHashMap()

    override suspend fun idFor(idType: IdType<*, *>, hint: Any?): Id {
        return idGeneratorFactories
            .computeIfAbsent(idType) {
                IdGeneratorWrapper(it, loadIdGenerator(it))
            }
            .generateId(hint)
    }

    private fun loadIdGenerator(it: IdType<*, *>): IdGenerator {
        return ServiceLoader
            .load(IdGeneratorFactory::class.java)
            .asSequence()
            .mapNotNull { factory ->
                factory.createFor(kjoringContext, it)
            }
            .toList()
            .also {
                if (it.size > 1) {
                    error("Multiple IdGeneratorFactories found for $it")
                }
            }
            .singleOrNull()
            ?: error("No IdGenerator found for $it")
    }
}
