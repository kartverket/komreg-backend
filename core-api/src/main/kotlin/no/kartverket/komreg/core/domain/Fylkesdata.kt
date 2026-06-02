package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

/**
 * Data for nytt fylke.
 */
@Serializable
data class Fylkesdata(
    val navn: String,
) : TypedPayload<Fylkesnummer>
