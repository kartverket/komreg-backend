package no.kartverket.komreg.integration.spi

import kotlinx.coroutines.flow.Flow
import no.kartverket.komreg.core.Product

interface EntitySource<out A : Product<*>> {
    val entityFlow: Flow<SourceEntityContext<A>>
}
