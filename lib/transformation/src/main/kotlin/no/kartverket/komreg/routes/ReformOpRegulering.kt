package no.kartverket.komreg.routes

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.kartverket.komreg.transformation.reform.ReformOp

@Serializable
data class ReformOpRegulering(
    override val navn: String,
    override val ikrafttredelse: LocalDate,
    val ops: List<ReformOp>
) : Regulering
