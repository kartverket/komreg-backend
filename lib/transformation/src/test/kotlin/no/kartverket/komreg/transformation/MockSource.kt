package no.kartverket.komreg.transformation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.EntitySource

class MockSource(override val entityFlow: Flow<Entity>) : EntitySource {
    override val id = "MockSource"
}

fun mockSource(vararg entities: Entity) = MockSource(entities.asFlow())
