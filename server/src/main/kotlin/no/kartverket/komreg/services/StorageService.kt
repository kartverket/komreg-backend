package no.kartverket.komreg.services

import kotlinx.coroutines.flow.Flow
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.transformation.Storage

class StorageService(private val transformationRepo: TransformationRepo) : Storage {
    override fun writeTransformationsToDatabase(kjoringId: Int, transformResultList: List<Transformation>) {
        transformationRepo.writeTransformationsToDatabase(kjoringId, transformResultList)
    }

    override fun readTransformationsFromDatabase(kjoringId: Int): Flow<Transformation> {
        return transformationRepo.readTransformationFromDatabase(kjoringId)
    }
}
