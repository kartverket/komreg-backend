package no.kartverket.komreg.transformation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.EntitySource

class MockSource(override val entityFlow: Flow<Entity>) : EntitySource {
    override val id = "MockSource"
    override val preValidation: Set<() -> Unit>
        get() = TODO("Not yet implemented")
    override val postValidation: Set<() -> Unit>
        get() = TODO("Not yet implemented")
}

fun mockSource(vararg entities: Entity) = MockSource(entities.asFlow())
