package no.kartverket.komreg.transformation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import no.kartverket.komreg.integration.spi.EntitySink
import no.kartverket.komreg.integration.spi.Transformation

class TestStorage : Storage {
    private val transformations: MutableList<Transformation> = mutableListOf()
    private val mockSink = MockSink()

    override fun writeTransformationsToDatabase(kjoringId: Int, transformResultList: List<Transformation>) {
        transformations.addAll(transformResultList)
    }

    override fun readTransformationsFromDatabase(kjoringId: Int): Flow<Transformation> {
        return transformations.asFlow()
    }

    override fun createTilbakeforingsstatusForKjoring(kjoringId: Int, entitySinks: List<EntitySink>) {
        return
    }

    override fun setTilbakeforingsStatusForSink(
        sink: EntitySink,
        status: String,
        kjoringId: Int,
        erOppretting: Boolean,
    ) {
        return
    }

    override fun hentIkkeStartedeTilbakeforingerForNyeEntiteter(kjoringId: Int): List<String> {
        return listOf(mockSink.id)
    }

    override fun hentIkkeStartedeTilbakeforingerForErstattendeEntiteter(kjoringId: Int): List<String> {
        return listOf(mockSink.id)
    }

    override fun setStatusForKjoring(kjoringId: Int, status: String) {
        return
    }
}
