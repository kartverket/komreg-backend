package no.kartverket.komreg.integration.spi

import kotlinx.coroutines.flow.Flow
import no.kartverket.komreg.core.KrAppBootContext

interface WriteService<A> {
    suspend fun write(a: Flow<A>)
}

interface WriteServiceFactory<A> {
    fun KrAppBootContext.create(): WriteService<A>
}
