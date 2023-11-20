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

    override fun createTilbakeføringsstatusForKjoring(kjoringId: Int, entitySinks: List<EntitySink>) {
        return
    }

    override fun setTilbakeføringsStatusForSink(
        sink: EntitySink,
        status: String,
        kjoringId: Int,
        erOppretting: Boolean,
    ) {
        return
    }

    override fun hentIkkeStartedeTilbakeføringerForNyeEntiteter(kjoringId: Int): List<String> {
        return listOf(mockSink.id)
    }

    override fun hentIkkeStartedeTilbakeføringerForErstattendeEntiteter(kjoringId: Int): List<String> {
        return listOf(mockSink.id)
    }

    override fun setStatusForKjøring(kjoringId: Int, status: String) {
        return
    }
}
