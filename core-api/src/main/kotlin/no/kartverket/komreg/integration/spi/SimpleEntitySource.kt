package no.kartverket.komreg.integration.spi

import kotlinx.coroutines.flow.Flow
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.data.RawData

data class Entity<A>(val id: Int, val data: A)

interface SimpleEntitySource<A : RawData<*>> {
    val entityFlow: Flow<A>
}

interface SimpleEntitySourceFactory<A : RawData<*>> {
    fun KrAppBootContext.create(): SimpleEntitySource<A>
}
