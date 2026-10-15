package no.kartverket.komreg.regulering.api.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReformExecution(val id: Int) {
    @Serializable
    enum class Status {
        PREPARED,
        SENT,
        SEND_REJECTED,
        SEND_UNKNOWN,
        START_REJECTED,
        START_UNKNOWN,
        START_ACCEPTED
    }
}
