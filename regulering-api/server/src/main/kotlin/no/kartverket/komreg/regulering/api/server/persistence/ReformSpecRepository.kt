package no.kartverket.komreg.regulering.api.server.persistence

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.komreg.regulering.api.server.persistence.jooq.tables.references.REFORM
import no.kartverket.komreg.regulering.reform.format.ReformSpec
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

/** Sammendrag av en lagret reform. Avledes fra specen ved lagring, slik at listing ikke leser specen. */
@Serializable
data class ReformSammendrag(val id: Int, val navn: String, val ikrafttredelse: LocalDate)

/** Lagrer [ReformSpec] som kilde til sannhet. */
interface ReformSpecRepository {
    suspend fun insert(spec: ReformSpec): Int

    /** `false` hvis reformen [id] ikke finnes. */
    suspend fun update(id: Int, spec: ReformSpec): Boolean

    suspend fun find(id: Int): ReformSpec?

    suspend fun list(): List<ReformSammendrag>

    /** `false` hvis reformen [id] ikke finnes. */
    suspend fun delete(id: Int): Boolean
}

class JooqReformSpecRepository(
    private val sql: DSLContext,
    private val json: Json,
) : ReformSpecRepository {
    private val utcNow = DSL.field(DSL.raw("(now() AT TIME ZONE 'UTC')"), SQLDataType.LOCALDATETIME)

    private fun ReformSpec.toJsonb() = JSONB.valueOf(json.encodeToString(ReformSpec.serializer(), this))

    override suspend fun insert(spec: ReformSpec): Int =
        sql.insertInto(REFORM)
            .set(REFORM.SPEC, spec.toJsonb())
            .set(REFORM.NAME, spec.navn)
            .set(REFORM.DATE, spec.ikrafttredelse.toJavaLocalDate())
            .returningResult(REFORM.ID)
            .awaitSingle()
            .value1()!!

    override suspend fun update(id: Int, spec: ReformSpec): Boolean =
        sql.update(REFORM)
            .set(REFORM.SPEC, spec.toJsonb())
            .set(REFORM.NAME, spec.navn)
            .set(REFORM.DATE, spec.ikrafttredelse.toJavaLocalDate())
            .set(REFORM.UPDATED_AT_UTC, utcNow)
            .where(REFORM.ID.eq(id))
            .awaitSingle() > 0

    override suspend fun find(id: Int): ReformSpec? =
        sql.select(REFORM.SPEC)
            .from(REFORM)
            .where(REFORM.ID.eq(id))
            .awaitFirstOrNull()
            ?.value1()
            ?.let { json.decodeFromString(ReformSpec.serializer(), it.data()) }

    override suspend fun list(): List<ReformSammendrag> =
        sql.select(REFORM.ID, REFORM.NAME, REFORM.DATE)
            .from(REFORM)
            .orderBy(REFORM.ID)
            .asFlow()
            .map { (id, name, date) -> ReformSammendrag(id!!, name!!, date!!.toKotlinLocalDate()) }
            .toList()

    override suspend fun delete(id: Int): Boolean =
        sql.deleteFrom(REFORM).where(REFORM.ID.eq(id)).awaitSingle() > 0
}
