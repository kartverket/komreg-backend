package no.kartverket.komreg

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.mapIndexed
import arrow.fx.coroutines.resourceScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromStream
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

private const val INSERT_SQL = "INSERT INTO id_cache (cache_hint, id_type, id) VALUES (?, ?::jsonb, ?::jsonb)"
private const val QUERY_SQL = "SELECT id FROM id_cache WHERE cache_hint = ? AND id_type = ?::jsonb FOR UPDATE"
private val QUERY_BATCH_SQL =
    """SELECT 
         (SELECT id_cache.id FROM id_cache WHERE id_cache.cache_hint = q.cache_hint FOR UPDATE)
       FROM 
         unnest(?, array(SELECT generate_series(1, ?))) AS q(cache_hint, index)
       ORDER BY q.index
    """.trimIndent()

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

        @OptIn(ExperimentalSerializationApi::class)
        suspend fun generateIds(hints: NonEmptyList<String?>): List<Pair<String?, Id>> {
            return hints.map { it to generateId(it) }
            // TKR-437: Midlertidig kommentert ut
//            return withContext(Dispatchers.IO) {
//                resourceScope {
//                    val conn = autoCloseable { dataSource.connection }
//                    conn.autoCommit = false
//                    val queryStmt = autoCloseable {
//                        conn.prepareStatement(QUERY_BATCH_SQL).apply {
//                            setArray(1, install(
//                                acquire = { conn.createArrayOf("text", hints.toTypedArray()) },
//                                release = { array, _ -> array.free() }
//                            ))
//                            setInt(2, hints.size)
//                        }
//                    }
//                    val insertStmt = autoCloseable {
//                        conn.prepareStatement(INSERT_SQL).apply {
//                            setString(2, idTypeJson)
//                        }
//                    }
//
//                    val cachedIds = flow {
//                        resourceScope {
//                            val rs = autoCloseable { queryStmt.executeQuery() }
//                            while (rs.next()) {
//                                val idValue = rs.getBinaryStream(1)
//                                    ?.takeIf { !rs.wasNull() }
//                                    ?.let { json.decodeFromStream(idType.valueSerializer, it) }
//                                emit(idValue)
//                            }
//                        }
//                    }
//
//                    cachedIds
//                        .mapIndexed { index, cachedIdValue ->
//                            val hint = hints[index]
//                            hint to if (cachedIdValue != null) {
//                                Id(idType, cachedIdValue)
//                            } else {
//                                idGenerator.generateId(hint).also { newId ->
//                                    if (hint != null) {
//                                        insertStmt.setString(1, hint)
//                                        insertStmt.setString(
//                                            3,
//                                            json.encodeToString(idType.valueSerializer, newId.typedValue(idType)!!)
//                                        )
//                                        insertStmt.addBatch()
//                                    }
//                                }
//                            }
//                        }
//                        .toList()
//                        .also {
//                            insertStmt.executeBatch()
//                            conn.commit()
//                        }
//                }
//            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        suspend fun generateId(hint: Any?): Id {
            return idGenerator.generateId(hint)
            // TKR-437: Midlertidig kommentert ut
//            if (hint == null) {
//                return idGenerator.generateId(null)
//            }
//            return withContext(Dispatchers.IO) {
//                resourceScope {
//                    val conn = autoCloseable { dataSource.connection }
//                    conn.autoCommit = false
//                    val stmt = autoCloseable {
//                        conn.prepareStatement(QUERY_SQL)
//                    }
//                    val rs = autoCloseable {
//                        stmt.setString(1, hint.toString())
//                        stmt.setString(2, idTypeJson)
//                        stmt.executeQuery()
//                    }
//                    if (rs.next()) {
//                        Id(idType, json.decodeFromStream(idType.valueSerializer, rs.getBinaryStream(1)))
//                    } else {
//                        idGenerator
//                            .generateId(hint)
//                            .also { id ->
//                                val insertStmt = autoCloseable {
//                                    conn.prepareStatement(INSERT_SQL)
//                                }
//                                insertStmt.setString(1, hint.toString())
//                                insertStmt.setString(2, idTypeJson)
//                                insertStmt.setString(3,
//                                    json.encodeToString(idType.valueSerializer, id.typedValue(idType)!!)
//                                )
//                                insertStmt.executeUpdate()
//                            }
//                    }.also {
//                        conn.commit()
//                    }
//                }
//            }
        }

    }

    private val idGeneratorWrappers: ConcurrentMap<IdType<*, *>, IdGeneratorWrapper<*, *>> = ConcurrentHashMap()

    override suspend fun idFor(idType: IdType<*, *>, hint: Any?): Id {
        return idGeneratorWrappers
            .computeIfAbsent(idType) {
                IdGeneratorWrapper(it, loadIdGenerator(it))
            }
            .generateId(hint)
    }

    override suspend fun idsFor(idType: IdType<*, *>, hints: List<String?>): List<Pair<String?, Id>> {
        return if (hints.size > 1) {
            idGeneratorWrappers
                .computeIfAbsent(idType) {
                    IdGeneratorWrapper(it, loadIdGenerator(it))
                }
                .generateIds(hints.toNonEmptyListOrNull()!!)
        } else if (hints.isNotEmpty()) {
            listOf(hints[0] to idFor(idType, hints[0]))
        } else {
            emptyList()
        }
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
