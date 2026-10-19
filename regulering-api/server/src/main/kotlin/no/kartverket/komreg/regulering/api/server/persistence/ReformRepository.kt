package no.kartverket.komreg.regulering.api.server.persistence


import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import no.kartverket.komreg.regulering.api.server.dto.ReformExecution
import no.kartverket.komreg.regulering.api.server.persistence.jooq.enums.ReformExecutionStatus
import no.kartverket.komreg.regulering.api.server.persistence.jooq.tables.references.REFORM
import no.kartverket.komreg.regulering.api.server.persistence.jooq.tables.references.REFORM_EXECUTION
import no.kartverket.komreg.regulering.api.server.service.reform.Reform
import org.jooq.DSLContext
import org.jooq.impl.DSL.multiset
import org.jooq.impl.DSL.select

class ReformRepository(
    private val sql: DSLContext
) {
    val summaries: Flow<Reform.Summary>
        get() {
            //@formatter:off
            val latestStatus = multiset(
                select(REFORM_EXECUTION.STATUS)
                    .from(REFORM_EXECUTION)
                    .where(REFORM_EXECUTION.REFORM_ID.eq(REFORM.ID))
                    .orderBy(
                        REFORM_EXECUTION.UPDATED_AT_UTC.desc(),
                        REFORM_EXECUTION.ID.desc()
                    )
                    .limit(1)
            )
            //@formatter:on

            return sql
                .select(REFORM.ID, REFORM.NAVN, latestStatus)
                .from(REFORM)
                .asFlow()
                .map { (id, name, statuses) ->
                    val status = when (statuses.firstOrNull()?.value1()) {
                        null -> ReformExecution.Status.SEND_UNKNOWN
                        ReformExecutionStatus.prepared -> ReformExecution.Status.PREPARED
                        ReformExecutionStatus.sent -> ReformExecution.Status.SENT
                        ReformExecutionStatus.send_rejected -> ReformExecution.Status.SEND_REJECTED
                        ReformExecutionStatus.send_unknown -> ReformExecution.Status.SEND_UNKNOWN
                        ReformExecutionStatus.start_rejected -> ReformExecution.Status.START_REJECTED
                        ReformExecutionStatus.start_unknown -> ReformExecution.Status.START_UNKNOWN
                        ReformExecutionStatus.start_accepted -> ReformExecution.Status.START_ACCEPTED
                    }
                    Reform.Summary(id!!, name!!, status)
                }
        }
}
