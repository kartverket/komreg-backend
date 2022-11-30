package no.kartverket.komreg.matrikkelen

import arrow.fx.coroutines.parMapUnordered
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
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
import org.rocksdb.*
import java.lang.System.Logger.Level.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MatrikkelenhetEntitySource(private val connection: OracleConnection) : EntitySource<Matrikkelenhet> {


    @OptIn(FlowPreview::class)
    override fun download(context: EntitySourceDownloadContext): Flow<SourceEntity<Matrikkelenhet>> =
        downloadThenSort(context).buffer(65536).parMapUnordered(concurrency = Runtime.getRuntime().availableProcessors(), transform = ::validateMatrikkelenhetGroup).flowOn(Dispatchers.Default)

    /**
     * Flow av alle matrikkelenhetrader fra databasen, sortert ved å putte radene på disk (RocksDB)
     */
    private fun downloadThenSort(context: EntitySourceDownloadContext): Flow<List<MatrikkelenhetRow>> = channelFlow {
        val cache = context.rocksDB
        val writeOpts = WriteOptions().apply {
            setSync(false)
            setDisableWAL(true)
        }
        val keySize = Long.SIZE_BYTES * 2 + Int.SIZE_BYTES * 2
        cache.createColumnFamily(columnFamilyDescriptor).use { cf ->
            val rowChannel = Channel<MatrikkelenhetRow>(capacity = 65536)

            val jdbcJob = launch(Dispatchers.IO.limitedParallelism(1)) {
                val query = "" +
                        "SELECT m.id, m.kommuneid, m.gardsnr, m.bruksnr, m.festenr, m.seksjonsnr " +
                        "FROM matrikkelenhet m "
                connection.prepareStatement(query).use { st ->
                    logger.log(INFO) { "Executing matrikkelenhet download query" }
                    st.executeQuery().use { rs ->
                        logger.log(INFO) { "Started receiving matrikkelenheter" }
                        while (rs.next()) {
                            rowChannel.send(MatrikkelenhetRow(
                                rs.getLong(1),
                                rs.getLong(2),
                                rs.getInt(3),
                                rs.getInt(4),
                                rs.getInt(5),
                                rs.getInt(6)))
                        }
                        logger.log(INFO) { "All matrikkelenheter received" }
                    }
                }
            }

            val cacheWriters =
                sequence {
                    repeat(Runtime.getRuntime().availableProcessors() * 8) {
                        yield(launch(Dispatchers.IO) {
                            while(true) {
                                val row = rowChannel
                                    .receiveCatching()
                                    .apply { exceptionOrNull()?.let { throw it } }
                                    .getOrNull()
                                    ?: break

                                val key = ByteBuffer
                                    .allocate(keySize)
                                    .order(ByteOrder.BIG_ENDIAN)
                                    .apply {
                                        putLong(row.kommuenummer)
                                        putInt(row.gardsnummer)
                                        putInt(row.bruksnummer)
                                        putLong(row.id)
                                    }
                                    .array()

                                cache.put(cf, writeOpts, key, ProtoBuf.encodeToByteArray(row))
                            }
                        })
                    }
                }.toList()

            jdbcJob.join()
            rowChannel.close()
            cacheWriters.joinAll()

            cache.flush(FlushOptions(), cf)
            cache.compactRange(cf)

            cache.newIterator(cf).use { iter ->
                iter.seekToFirst()
                if (iter.isValid) {
                    val keyBuf = ByteBuffer.allocate(keySize)
                    val rows = mutableListOf<MatrikkelenhetRow>()
                    iter.key(keyBuf)
                    var prevKommuneId = keyBuf.long
                    var prevGardsnummer = keyBuf.int
                    var prevBruksnummer = keyBuf.int
                    do {
                        keyBuf.rewind()
                        iter.key(keyBuf)
                        val kommuneId = keyBuf.long
                        val gardsnummer = keyBuf.int
                        val bruksnummer = keyBuf.int
                        if (prevKommuneId != kommuneId || prevGardsnummer != gardsnummer || prevBruksnummer != bruksnummer) {
                            send(rows.toImmutableList())
                            prevKommuneId = kommuneId
                            prevGardsnummer = gardsnummer
                            prevBruksnummer = bruksnummer
                            rows.clear()
                        }
                        rows.add(ProtoBuf.decodeFromByteArray(iter.value()))
                        iter.next()
                    } while (iter.isValid)
                }
            }
        }
    }

    /**
     * Flow av alle matrikkelenheterader fra databasen, sortert av databasen
     */
    private fun downloadGroupedRows(): Flow<List<MatrikkelenhetRow>> = channelFlow {
        val query = "" +
                "SELECT m.id, m.kommuneid, m.gardsnr, m.bruksnr, m.festenr, m.seksjonsnr " +
                "FROM matrikkelenhet m " +
                "ORDER BY m.kommuneid, m.gardsnr, m.bruksnr, m.id"

        connection.prepareStatement(query).use { st ->
            logger.log(INFO) { "Executing matrikkelenhet download query" }
            withContext(Dispatchers.IO.limitedParallelism(1)) { st.executeQuery() }.use { rs ->
                val rows = mutableListOf<MatrikkelenhetRow>()
                if (rs.next()) {
                    logger.log(INFO) { "Started receiving matrikkelenheter" }
                    var prevKommuneid = rs.getLong(2)
                    var prevGardsnummer = rs.getInt(3)
                    var prevBruksnummer = rs.getInt(4)
                    do {
                        val matrikkelenhetId = rs.getLong(1)
                        val kommuneId = rs.getLong(2)
                        val gardsnummer = rs.getInt(3)
                        val bruksnummer = rs.getInt(4)
                        if (prevKommuneid != kommuneId || prevGardsnummer != gardsnummer || prevBruksnummer != bruksnummer) {
                            send(rows.toImmutableList())
                            prevKommuneid = kommuneId
                            prevGardsnummer = gardsnummer
                            prevBruksnummer = bruksnummer
                            rows.clear()
                        }
                        rows.add(MatrikkelenhetRow(matrikkelenhetId, kommuneId, gardsnummer, bruksnummer, rs.getInt(5), rs.getInt(6)))
                    } while (rs.next())
                    logger.log(INFO) { "All matrikkelenheter received" }
                }
            }
        }
    }

    private fun validateMatrikkelenhetGroup(relatedRows: List<MatrikkelenhetRow>): SourceEntity<Matrikkelenhet> {
        val missingGrunneiendom: Valid<Pair<Id<Matrikkelenhet>, Matrikkelenhet>> = Validation.valid(
            GeneratedId<Matrikkelenhet>() to relatedRows
                .run { first() }
                .run { Grunneiendom(Math.toIntExact(kommuenummer), gardsnummer, bruksnummer, persistentSetOf(), persistentSetOf()) })
        var validEmptyGrunneiendom: Valid<Pair<Id<Matrikkelenhet>, Matrikkelenhet>> = missingGrunneiendom
        val festegrunnRows: MutableMap<Int, Validation<MatrikkelenhetRow>> = mutableMapOf()
        val festegrunnSeksjonRows: MutableMap<Int, MutableMap<Int, Validation<MatrikkelenhetRow>>> = mutableMapOf()
        val seksjonRows: MutableMap<Int, Validation<MatrikkelenhetRow>> = mutableMapOf()
        for (row in relatedRows) {
            if (row.festenummer == 0 && row.seksjonsnummer == 0) {
                if (validEmptyGrunneiendom == missingGrunneiendom) {
                    validEmptyGrunneiendom = Validation.valid(SourceId<Matrikkelenhet>(row.id) to Grunneiendom(Math.toIntExact(row.kommuenummer), row.gardsnummer, row.bruksnummer, emptySet(), emptySet()))
                } else {
                    validEmptyGrunneiendom.log(ERROR) {
                        "Det finnes flere id-er for grunneiendom på matrikkelenhet ${row.gardsnummer}/${row.bruksnummer}: ${row.id}"
                    }
                }
            } else if (row.festenummer != 0) {
                if (row.seksjonsnummer == 0) {
                    festegrunnRows.merge(row.festenummer, Validation.valid(row)) { validation, _ ->
                        val existingId = validation.map { it.id }.orNull()
                        validation.log(ERROR) { "Det finnes flere festegrunner med festenummer ${row.festenummer} på matrikkelenhet ${row.gardsnummer}/${row.bruksnummer}: $existingId og ${row.id}" }
                    }
                } else {
                    festegrunnSeksjonRows
                        .computeIfAbsent(row.festenummer) { mutableMapOf() }
                        .merge(row.seksjonsnummer, Validation.valid(row)) { validation, _ ->
                            val existingId = validation.map { it.id }.orNull()
                            validation.log(ERROR) { "Det finnes flere festegrunner med seksjonsnummer ${row.seksjonsnummer} på festegrunn ${row.gardsnummer}/${row.bruksnummer}/${row.festenummer}: $existingId og ${row.id}" }
                        }
                }
            } else {
                seksjonRows.merge(row.seksjonsnummer, Validation.valid(row)) { validation, _ ->
                    val existingId = validation.map { it.id }.orNull()
                    validation.log(ERROR) { "Det finnes flere seksjoner med seksjonsnummer ${row.seksjonsnummer} på matrikkelenhet ${row.gardsnummer}/${row.bruksnummer}: $existingId og ${row.id}" }
                }
            }
        }

        val validFestegrunner = festegrunnRows.keys.union(festegrunnSeksjonRows.keys)
            .map { festenummer ->
                val validSeksjonRows = festegrunnSeksjonRows[festenummer].orEmpty().values.toValidList()
                val validFestegrunnRow = festegrunnRows[festenummer] ?: Validation.invalid(
                    ERROR,
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

        val grunneiendomId = validEmptyGrunneiendom.value.first

        return Validation.productMap(validEmptyGrunneiendom, validFestegrunner, validSeksjoner) { (_,grunneiendom), festegrunner, seksjoner ->
            Grunneiendom(grunneiendom.kommunenr, grunneiendom.gardsnummer, grunneiendom.bruksnummer, festegrunner, seksjoner)
        }.fold({ warns, errs -> when(grunneiendomId) {
            is GeneratedId -> VirtualEntity(grunneiendomId, Invalid(errs, warns))
            is SourceId ->  DatabaseEntity(grunneiendomId, Invalid(errs, warns))
        } }) { warns, grunneiendom ->
            when(grunneiendomId) {
                is GeneratedId -> VirtualEntity(grunneiendomId, Valid(grunneiendom, warns))
                is SourceId -> DatabaseEntity(grunneiendomId, Valid(grunneiendom, warns))
            }
        }
    }

    @Serializable
    data class MatrikkelenhetRow(
        val id: Long,
        val kommuenummer: Long,
        val gardsnummer: Int,
        val bruksnummer: Int,
        val festenummer: Int,
        val seksjonsnummer: Int)

    companion object {
        private val logger = System.getLogger(::MatrikkelenhetEntitySource::class.java.name)
        private val columnFamilyDescriptor = ColumnFamilyDescriptor(
            "matrikkelenetRow".toByteArray(),
            ColumnFamilyOptions().apply {
                setDisableAutoCompactions(true)
            }
        )
    }
}


