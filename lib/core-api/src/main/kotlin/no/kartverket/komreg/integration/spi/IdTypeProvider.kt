package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.domain.IdType

/**
 * Gir [IdType] for nye entiteter uten kilde (`ReformOp.Create`). Valgfri: en plugin uten provider kan ikke få
 * opprettet entiteter uten kilde.
 */
interface IdTypeProvider {
    /** `null` hvis provideren ikke kjenner [identType]. */
    suspend fun idTypeFor(identType: IdentOrEmptyType<*>): IdType<*, *>?
}
