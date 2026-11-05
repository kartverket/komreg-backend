package no.kartverket.komreg.regulering.api.server.persistence


import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.associateBy
import kotlinx.coroutines.flow.groupBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import no.kartverket.komreg.regulering.api.server.domain.type.TitleNumber
import no.kartverket.komreg.regulering.api.server.persistence.jooq.tables.references.REFORM
import no.kartverket.komreg.regulering.api.server.persistence.jooq.tables.references.REFORM_CADASTRAL_UNIT
import no.kartverket.komreg.regulering.api.server.route.regulering.GrensejusteringDraft
import no.kartverket.komreg.regulering.api.server.route.regulering.MatrikkelenhetDraft
import no.kartverket.komreg.regulering.api.server.route.regulering.ReguleringDetail
import org.jooq.DSLContext
import org.jooq.impl.DSL.excluded
import org.jooq.impl.DSL.row
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlinx.coroutines.flow.asFlow as toFlow

class ReformRepository(
    private val sql: DSLContext
) {
    suspend fun all(): Flow<ReguleringDetail> {
        val records= sql.selectFrom(REFORM).asFlow().associateBy { it.id!! }
        return sql.selectFrom(REFORM_CADASTRAL_UNIT)
            .asFlow()
            .groupBy { Triple(it.reformId, it.municipalityNumber, it.newMunicipalityNumber) }
            .entries
            .toFlow()
            .map { (k, rs) ->
                val (reformId, fromMunicipality, toMunicipality) = k
                val drafts = rs.mapTo(hashSetOf()) {
                    MatrikkelenhetDraft(
                        fromMunicipality,
                        it.landNumber,
                        it.titleNumber?.toInt(),
                        emptySet(),
                        it.newLandNumber,
                        it.newTitleNumber?.toInt())
                }
                val reform = records[reformId]!!
                ReguleringDetail(reformId, reform.name, reform.date.toKotlinLocalDate(), GrensejusteringDraft(fromMunicipality, toMunicipality, drafts))
            }


        }

    suspend operator fun set(id: Int, regulering: ReguleringDetail) {
        sql.transactionCoroutine { cfg ->
            val tx = cfg.dsl()

            tx.insertInto(REFORM, REFORM.ID, REFORM.NAME, REFORM.DATE)
                .values(id, regulering.navn, regulering.dato.toJavaLocalDate())
                .onConflict(REFORM.ID)
                .doUpdate()
                .set(REFORM.NAME, excluded(REFORM.NAME))
                .set(REFORM.DATE, excluded(REFORM.DATE))
                .awaitSingle()

            // Exclusion constraints can't be ON CONFLICT targets, so replace all units for the reform
            tx.deleteFrom(REFORM_CADASTRAL_UNIT)
                .where(REFORM_CADASTRAL_UNIT.REFORM_ID.eq(id))
                .awaitSingle()

            val (fra, til, enheter) = regulering.grensejustering
            if (enheter.isEmpty()) return@transactionCoroutine

            tx.insertInto(
                REFORM_CADASTRAL_UNIT,
                REFORM_CADASTRAL_UNIT.REFORM_ID,
                REFORM_CADASTRAL_UNIT.MUNICIPALITY_NUMBER,
                REFORM_CADASTRAL_UNIT.LAND_NUMBER,
                REFORM_CADASTRAL_UNIT.TITLE_NUMBER,
                REFORM_CADASTRAL_UNIT.NEW_MUNICIPALITY_NUMBER,
                REFORM_CADASTRAL_UNIT.NEW_LAND_NUMBER,
                REFORM_CADASTRAL_UNIT.NEW_TITLE_NUMBER,
            )
                .valuesOfRows(enheter.map {
                    row(
                        id,
                        fra,
                        it.gardsnummer,
                        it.bruksnummer?.let { bnr -> TitleNumber(bnr).value },
                        til,
                        it.nyGardsnummer ?: it.gardsnummer,
                        it.bruksnummer?.let { bnr -> TitleNumber(it.nyBruksnummer ?: bnr).value },
                    )
                })
                .awaitSingle()
        }
    }
}
