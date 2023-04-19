package no.kartverket.komreg.transformation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.EntitySource
import no.kartverket.komreg.integration.spi.Ident

fun mockEntitySource(init: suspend MockEntitySourceBuilder.() -> Unit): EntitySource {
    return MockEntitySource(flow {
        MockEntitySourceBuilder(this)
            .init()
    })
}

private class MockEntitySource(override val entityFlow: Flow<Entity>) : EntitySource {
    override val id: String = "MockEntitySource"

    override val preValidation: Set<() -> Unit> = emptySet()

    override val postValidation: Set<() -> Unit> = emptySet()
}

class MockEntitySourceBuilder(private val collector: FlowCollector<Entity>) {
    suspend fun entity(
        id: String,
        ident: Ident? = null,
        associatedIdents: Set<Ident>? = null
    ) {
        collector.emit(
            Entity(
                id,
                ident,
                associatedIdents,
            )
        )
    }
}
