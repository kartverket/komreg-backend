package no.kartverket.komreg

import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.transformation.getAllFylker

class FylkeService {

    suspend fun getFylker(): List<Fylke> {
        return getAllFylker()
    }
}
