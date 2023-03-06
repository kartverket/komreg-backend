package no.kartverket.komreg

import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.transformation.TransformationKommune

class KommuneService {

    suspend fun getKommuner(): List<Kommune> {
        return TransformationKommune().getAllKommuner()
    }

    suspend fun updateKommune(oldNumber: Long, newNumber: Long) {
        TransformationKommune().writeKommuneNummer(oldNumber, newNumber)
    }
}
