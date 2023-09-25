package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable
import no.kartverket.komreg.integration.spi.Payload

/**
 * Data for nytt fylke.
 */
@Serializable
data class Fylkesdata(
    val navn: String,
) : Payload
