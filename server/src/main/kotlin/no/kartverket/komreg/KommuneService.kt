package no.kartverket.komreg

import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.transformation.getAllKommuner

class KommuneService {

    suspend fun getKommuner(): List<Kommune> {
        return getAllKommuner()
    }
}
