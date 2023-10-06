package no.kartverket.komreg.repositories

import kotlinx.serialization.Serializable
import no.kartverket.komreg.integration.spi.Payload

@Serializable
data class TestPayload(
    val flagg: Boolean,
    val tekst: String,
    val idListe: Set<Long>
) : Payload
