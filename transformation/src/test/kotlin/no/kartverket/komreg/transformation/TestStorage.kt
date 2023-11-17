package no.kartverket.komreg.transformation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import no.kartverket.komreg.integration.spi.EntitySink
import no.kartverket.komreg.integration.spi.Transformation

class TestStorage : Storage {
    private val transformations: MutableList<Transformation> = mutableListOf()

    override fun writeTransformationsToDatabase(kjoringId: Int, transformResultList: List<Transformation>) {
        transformations.addAll(transformResultList)
    }

    override fun readTransformationsFromDatabase(kjoringId: Int): Flow<Transformation> {
        return transformations.asFlow()
    }

    override fun createTilbakeføringsstatusForKjoring(kjoringId: Int, entitySinks: List<EntitySink>) {
        return
    }

    override fun settStatusNyeEntiteterTilbakeført(sink: EntitySink, kjoringId: Int) {
        return
    }

    override fun settStatusErstattendeEntiteterTilbakeført(sink: EntitySink, kjoringId: Int) {
        return
    }
}
