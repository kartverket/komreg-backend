package no.kartverket.komreg.transformation

import kotlinx.coroutines.flow.Flow
import no.kartverket.komreg.integration.spi.EntitySink
import no.kartverket.komreg.integration.spi.Transformation
import java.time.LocalDate

class MockSink : EntitySink {
    val transformations = mutableListOf<Transformation>()

    override val id = "MockSink"

    override suspend fun consumeTransformations(flow: Flow<Transformation>, ikrafttredelsesdato: LocalDate) {
        flow.collect {
            transformations.add(it)
        }
    }

}
