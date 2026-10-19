package no.kartverket.komreg.regulering.api.server.service.reform

import kotlinx.serialization.Serializable
import no.kartverket.komreg.regulering.api.server.dto.ReformExecution

@Serializable
data class Reform(val id: Int) {
    @Serializable
    data class Summary(val id: Int, val name: String, val status: ReformExecution.Status)
}
