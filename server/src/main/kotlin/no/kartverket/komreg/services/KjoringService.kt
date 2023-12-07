package no.kartverket.komreg.services

import no.kartverket.komreg.repositories.Kjoring
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.Kjoringstatus
import java.lang.IllegalArgumentException

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

    fun opprettKjoring(reguleringId: String): Kjoring {
        val harOpprettedeKjoringerPaaRegulering =
            kjoringRepo.getStatusForKjoringMedReguleringsId(reguleringId).any { it.status == Kjoringstatus.OPPRETTET }

        if (harOpprettedeKjoringerPaaRegulering) {
            throw IllegalArgumentException("Det finnes allerede en kjøring på reguleringen som har status OPPRETTET")
        }

        return kjoringRepo.opprettKjoring(reguleringId)
    }

    fun hentKjoring(kjoringId: Int): Kjoring? {
        return kjoringRepo.getKjoring(kjoringId)
    }

    fun hentKjoringer(): List<Kjoring> {
        return kjoringRepo.getKjoringer()
    }

    fun setStatusForKjoring(kjoringId: Int, status: Kjoringstatus) {
        kjoringRepo.setStatusForKjøring(kjoringId, status)
    }

    fun startKjoring(kjoringId: Int) {
        kjoringRepo.startKjoring(kjoringId)

        // Kall på et eller annet sted om at skjemaet vi nå kjører mot blir satt til RUNNING
    }

    fun setKjoringFullført(kjoringId: Int) {
        kjoringRepo.setStatusForKjøring(kjoringId, Kjoringstatus.FERDIG)

        // Kall på et eller annet sted om at skjemaet vi kjørte mot er satt til DONE
    }

    fun finnStoppetKjøringForRegulering(reguleringId: String): Kjoring? {
        return kjoringRepo.finnStoppetKjøringForRegulering(reguleringId)
    }
}
