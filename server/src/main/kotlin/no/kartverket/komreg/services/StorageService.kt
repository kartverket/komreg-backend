package no.kartverket.komreg.services

import kotlinx.coroutines.flow.Flow
import no.kartverket.komreg.integration.spi.EntitySink
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.repositories.*
import no.kartverket.komreg.transformation.Storage

class StorageService(
    private val transformationRepo: TransformationRepo,
    private val tilbakeføringsstatusRepo: TilbakeføringsstatusRepo,
    private val kjoringRepo: KjoringRepo,
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

    override fun setTilbakeføringsStatusForSink(
        sink: EntitySink,
        status: String,
        kjoringId: Int,
        erOppretting: Boolean,
    ) {
        tilbakeføringsstatusRepo.setTilbakeføringsStatusForSink(
            sink,
            enumValueOf<TilbakeføringsstatusForSink.Status>(status),
            kjoringId,
            erOppretting,
        )
    }

    override fun hentIkkeStartedeTilbakeføringerForNyeEntiteter(kjoringId: Int): List<String> {
        return tilbakeføringsstatusRepo.hentIkkeStartedeTilbakeføringerForNyeEntiteter(kjoringId)
    }

    override fun hentIkkeStartedeTilbakeføringerForErstattendeEntiteter(kjoringId: Int): List<String> {
        return tilbakeføringsstatusRepo.hentIkkeStartedeTilbakeføringerForErstattendeEntiteter(kjoringId)
    }

    override fun setStatusForKjøring(kjoringId: Int, status: String) {
        kjoringRepo.setStatusForKjøring(kjoringId, Kjoringstatus.valueOf(status))
    }
}
