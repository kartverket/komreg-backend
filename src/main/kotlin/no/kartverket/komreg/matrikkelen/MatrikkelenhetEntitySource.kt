package no.kartverket.komreg.matrikkelen

import arrow.fx.coroutines.parMapUnordered
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.jdk9.asFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import no.kartverket.komreg.domain.FestegrunnData
import no.kartverket.komreg.domain.Grunneiendom
import no.kartverket.komreg.domain.Matrikkelenhet
import no.kartverket.komreg.domain.SeksjonData
import no.kartverket.komreg.experimental.*
import oracle.jdbc.OracleConnection
import oracle.jdbc.OraclePreparedStatement
import oracle.jdbc.OracleRow
import org.rocksdb.Options
import org.rocksdb.RocksDB
import java.io.File
import java.lang.System.Logger.Level
import java.util.UUID

class MatrikkelenhetEntitySource(private val connection: OracleConnection) : EntitySource<Matrikkelenhet> {

    override fun download(context: EntitySourceDownloadContext): Flow<Entity<Matrikkelenhet>> = flow {
        val rocksOpts = Options().apply {
            setCreateIfMissing(true)
        }
        val rocksFile = File(context.cacheDir, UUID.randomUUID().toString() + ".db").apply {
            deleteOnExit()
        }

        RocksDB.open(rocksOpts, rocksFile.canonicalPath).use { rocks ->
            connection
                .prepareStatement("SELECT m.id, m.kommuneid, m.gardsnr, m.bruksnr, m.festenr, m.seksjonsnr FROM matrikkelenhet m")
                .use { wrappedStatement ->
                    wrappedStatement.unwrap(OraclePreparedStatement::class.java).let { st ->
                        st.fetchSize = 10240
                        st.executeQueryAsyncOracle()
                            .asFlow()
                            .flatMapConcat { it.publisherOracle(MatrikkelenhetRow.Companion::from).asFlow() }
                            .buffer(st.fetchSize * 8)
                            .collect { row ->
                                val key = MatrikkelenhetKey(row.kommuenummer, row.gardsnummer, row.bruksnummer)
                                val keyBytes = ProtoBuf.encodeToByteArray(key)
                                if (!rocks.keyMayExist(keyBytes, null)) {
                                    rocks.put(keyBytes, ProtoBuf.encodeToByteArray(listOf(row)))
                                } else {
                                    val existingBytes = rocks.get(keyBytes)
                                    val bytes = if (existingBytes != null) {
                                        val exisiting: List<MatrikkelenhetRow> =
                                            ProtoBuf.decodeFromByteArray(existingBytes)
                                        ProtoBuf.encodeToByteArray(exisiting + row)
                                    } else {
                                        ProtoBuf.encodeToByteArray(listOf(row))
                                    }
                                    rocks.put(keyBytes, bytes)
                                }
                            }
                    }
                }

            val cacheFlow = flow {
                rocks.newIterator().use { iter ->
                    iter.seekToFirst()
                    while (iter.isValid) {
                        val key = ProtoBuf.decodeFromByteArray<MatrikkelenhetKey>(iter.key())
                        val value = ProtoBuf.decodeFromByteArray<List<MatrikkelenhetRow>>(iter.value())
                        emit (key to value)
                        iter.next()
                    }
                }
            }

            val entityFlow = cacheFlow
                .parMapUnordered { (key, relatedRows) ->
                    val (
                        validGrunneiendomRow,
                        validFestegrunner,
                        validSeksjoner
                    ) = groupMatrikkelenhet(relatedRows)
                    createMatrikkelenhetEntity(validGrunneiendomRow, validFestegrunner, validSeksjoner, key)
                }

            emitAll(entityFlow)
        }


    }

    private fun groupMatrikkelenhet(relatedRows: List<MatrikkelenhetRow>): Triple<Validation<MatrikkelenhetRow?>, Valid<Set<FestegrunnData.Detached>>, Valid<Set<SeksjonData.Detached>>> {
        var validGrunneiendomRow: Validation<MatrikkelenhetRow?> = Valid(null)
        val festegrunnRows: MutableMap<Int, Validation<MatrikkelenhetRow>> = mutableMapOf()
        val festegrunnSeksjonRows: MutableMap<Int, MutableMap<Int, Validation<MatrikkelenhetRow>>> = mutableMapOf()
        val seksjonRows: MutableMap<Int, Validation<MatrikkelenhetRow>> = mutableMapOf()
        for (row in relatedRows) {
            if (row.festenummer == 0 && row.seksjonsnummer == 0) {
                validGrunneiendomRow = if (validGrunneiendomRow is Valid && validGrunneiendomRow.value == null) {
                    Validation.valid(row)
                } else {
                    validGrunneiendomRow.log(Level.ERROR) {
                        "Det finnes flere id-er for grunneiendom på matrikkelenhet ${row.gardsnummer}/${row.bruksnummer}: ${row.id}"
                    }
                }
            } else if (row.festenummer != 0) {
                if (row.seksjonsnummer == 0) {
                    festegrunnRows.merge(row.festenummer, Validation.valid(row)) { validation, _ ->
                        val existingId = validation.map { it.id }.orNull()
                        validation.log(Level.ERROR) { "Det finnes flere festegrunner med festenummer ${row.festenummer} på matrikkelenhet ${row.gardsnummer}/${row.bruksnummer}: $existingId og ${row.id}" }
                    }
                } else {
                    festegrunnSeksjonRows
                        .computeIfAbsent(row.festenummer) { mutableMapOf() }
                        .merge(row.seksjonsnummer, Validation.valid(row)) { validation, _ ->
                            val existingId = validation.map { it.id }.orNull()
                            validation.log(Level.ERROR) { "Det finnes flere festegrunner med seksjonsnummer ${row.seksjonsnummer} på festegrunn ${row.gardsnummer}/${row.bruksnummer}/${row.festenummer}: $existingId og ${row.id}" }
                        }
                }
            } else {
                seksjonRows.merge(row.seksjonsnummer, Validation.valid(row)) { validation, _ ->
                    val existingId = validation.map { it.id }.orNull()
                    validation.log(Level.ERROR) { "Det finnes flere seksjoner med seksjonsnummer ${row.seksjonsnummer} på matrikkelenhet ${row.gardsnummer}/${row.bruksnummer}: $existingId og ${row.id}" }
                }
            }
        }

        val validFestegrunner = festegrunnRows.keys.union(festegrunnSeksjonRows.keys)
            .map { festenummer ->
                val validSeksjonRows = festegrunnSeksjonRows[festenummer].orEmpty().values.toValidList()
                val validFestegrunnRow = festegrunnRows[festenummer] ?: Validation.invalid(
                    Level.ERROR,
                    "Seksjoner festet på festegrunn som ikke finnes"
                )
                validFestegrunnRow.productMap(validSeksjonRows) { festegrunnRow, seksjonsRows ->
                    FestegrunnData.Detached(
                        SourceId(festegrunnRow.id),
                        festegrunnRow.festenummer,
                        seksjonsRows.map { seksjonsRow ->
                            SeksjonData.Detached(
                                SourceId(seksjonsRow.id),
                                seksjonsRow.seksjonsnummer
                            )
                        }.toSet()
                    )
                }
            }
            .toValidList()
            .map { it.toSet() }

        val validSeksjoner = seksjonRows.values.toValidList()
            .map { it.map { row -> SeksjonData.Detached(SourceId(row.id), row.seksjonsnummer) }.toSet() }
        return Triple(validGrunneiendomRow, validFestegrunner, validSeksjoner)
    }

    private fun createMatrikkelenhetEntity(
        validGrunneiendomRow: Validation<MatrikkelenhetRow?>,
        validFestegrunner: Valid<Set<FestegrunnData.Detached>>,
        validSeksjoner: Valid<Set<SeksjonData.Detached>>,
        key: MatrikkelenhetKey
    ) = Validation
        .productMap(
            validGrunneiendomRow,
            validFestegrunner,
            validSeksjoner
        ) { grunneiendomRow, festegrunnData, seksjonsData ->
            if (grunneiendomRow != null) {
                SourceId<Matrikkelenhet>(grunneiendomRow.id) to Grunneiendom(
                    Math.toIntExact(
                        grunneiendomRow.kommuenummer
                    ),
                    grunneiendomRow.gardsnummer,
                    grunneiendomRow.bruksnummer,
                    festegrunnData,
                    seksjonsData
                )
            } else {
                GeneratedId<Matrikkelenhet>() to Grunneiendom(
                    Math.toIntExact(key.kommuenummer),
                    key.gardsnummer,
                    key.bruksnummer,
                    festegrunnData,
                    seksjonsData
                )
            }
        }
        .fold({ warns, errs ->
            SourceEntity(
                GeneratedId<Matrikkelenhet>(),
                Invalid(errs, warns)
            )
        }) { warns, (id, grunneiendom) ->
            when (id) {
                is SourceId -> SourceEntity(id, Valid(grunneiendom, warns))
                is GeneratedId -> SourceEntity(
                    id,
                    Valid(grunneiendom, warns)
                        .log(Level.ERROR) { "Grunneiendom mangler for ${grunneiendom.kommunenr}-${grunneiendom.gardsnummer}/${grunneiendom.bruksnummer}" })
            }
        }
}

    @Serializable
    private data class MatrikkelenhetKey(val kommuenummer: Long,
                                         val gardsnummer: Int,
                                         val bruksnummer: Int)

    @Serializable
    private data class MatrikkelenhetRow(
        val id: Long,
        val kommuenummer: Long,
        val gardsnummer: Int,
        val bruksnummer: Int,
        val festenummer: Int,
        val seksjonsnummer: Int) {

        companion object {
            fun from(row: OracleRow) : MatrikkelenhetRow = MatrikkelenhetRow(
                row.getOrThrow(1),
                row.getOrThrow(2),
                row.getOrThrow(3),
                row.getOrThrow(4),
                row.getOrThrow(5),
                row.getOrThrow(6)
            )
        }
    }


inline fun <reified T> OracleRow.get(col: Int): T? = this.getObject(col, T::class.java)
inline fun <reified T> OracleRow.getOrThrow(col: Int): T = this.getObject(col, T::class.java)
