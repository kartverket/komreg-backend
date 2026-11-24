package no.kartverket.komreg.plugins.matrikkel

import no.kartverket.komreg.core.domain.IdType
import no.kartverket.komreg.integration.spi.IdTypeProvider
import no.kartverket.komreg.integration.spi.IdentOrEmptyType
import no.statkart.matrikkel.komreg.getFylkeIdentType
import no.statkart.matrikkel.komreg.getKommuneIdentType
import no.statkart.matrikkel.komreg.model.Matrikkeltyper

/**
 * Legg ikke til flere typer før en sink støtter `Create` for dem (se `v1Capabilities`).
 */
class MatrikkelIdTypeProvider : IdTypeProvider {
    override suspend fun idTypeFor(identType: IdentOrEmptyType<*>): IdType<*, *>? = when {
        identType === getFylkeIdentType() -> Matrikkeltyper.Fylke
        identType === getKommuneIdentType() -> Matrikkeltyper.Kommune
        else -> null
    }
}
