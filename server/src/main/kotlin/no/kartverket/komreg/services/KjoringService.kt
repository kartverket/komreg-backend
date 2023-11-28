package no.kartverket.komreg.services

import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.Kjoringstatus

class KjoringService(private val kjoringRepo: KjoringRepo) {
    fun handleShutdown() {
        kjoringRepo.getKjoringer().forEach { kjoring ->
            if (kjoring.status == Kjoringstatus.KJØRER) {
                kjoringRepo.setStatusForKjøring(
                    kjoring.id,
                    Kjoringstatus.AVBRUTT,
                )
            }
        }
    }
}
