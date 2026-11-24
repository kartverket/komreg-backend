package no.kartverket.komreg.integration

import no.kartverket.komreg.core.domain.IdType
import no.kartverket.komreg.integration.spi.IdTypeProvider
import no.kartverket.komreg.integration.spi.IdentOrEmptyType
import java.util.ServiceLoader

class IdTypeProviderManager(
    private val providers: List<IdTypeProvider> = ServiceLoader.load(IdTypeProvider::class.java).toList(),
) {
    suspend fun idTypeFor(identType: IdentOrEmptyType<*>): IdType<*, *>? {
        val found = providers.mapNotNull { it.idTypeFor(identType) }.distinct()
        check(found.size <= 1) { "Flere IdType for $identType: $found" }
        return found.singleOrNull()
    }
}
