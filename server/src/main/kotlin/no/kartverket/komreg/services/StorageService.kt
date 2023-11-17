package no.kartverket.komreg.services

import kotlinx.coroutines.flow.Flow
import no.kartverket.komreg.integration.spi.EntitySink
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.repositories.TilbakeføringsstatusRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.transformation.Storage

class StorageService(
    private val transformationRepo: TransformationRepo,
    private val tilbakeføringsstatusRepo: TilbakeføringsstatusRepo,
) : Storage {
    override fun writeTransformationsToDatabase(kjoringId: Int, transformResultList: List<Transformation>) {
        transformationRepo.writeTransformationsToDatabase(kjoringId, transformResultList)
    }

    override fun readTransformationsFromDatabase(kjoringId: Int): Flow<Transformation> {
        return transformationRepo.readTransformationFromDatabase(kjoringId)
    }

    override fun createTilbakeføringsstatusForKjoring(kjoringId: Int, entitySinks: List<EntitySink>) {
        tilbakeføringsstatusRepo.createTilbakeføringsstatusForKjoring(kjoringId, entitySinks)
    }

    override fun settStatusNyeEntiteterTilbakeført(sink: EntitySink, kjoringId: Int) {
        tilbakeføringsstatusRepo.leggTilIkkeStartedeSinkerForNyeEntiteter(sink, kjoringId)
    }

    override fun settStatusErstattendeEntiteterTilbakeført(sink: EntitySink, kjoringId: Int) {
        tilbakeføringsstatusRepo.leggTilIkkeStartedeSinkerForErstattendeEntiteter(sink, kjoringId)
    }
}
