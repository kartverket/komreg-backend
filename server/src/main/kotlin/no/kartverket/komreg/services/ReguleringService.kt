package no.kartverket.komreg.services

import io.ktor.server.plugins.NotFoundException
import no.kartverket.komreg.exceptions.ReguleringAlreadyFinishedException
import no.kartverket.komreg.exceptions.ReguleringAlreadyRunningException
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.Kjoringstatus
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.routes.Regulering
import no.kartverket.komreg.validation.ReguleringValidator

class ReguleringService(
    private val reguleringRepo: ReguleringRepo,
    private val kjoringRepo: KjoringRepo,
) {

    fun getOrThrowRegulering(
        regId: String,
    ): Regulering {
        val regulering = reguleringRepo.getReguleringById(regId)
            ?: throw NotFoundException("Fant ingen regulering for regId: $regId")

        kjoringRepo.getStatusForKjoringMedReguleringsId(regId).any { it.status === Kjoringstatus.KJØRER }
            .let { if (it) throw ReguleringAlreadyRunningException(regId) }

        kjoringRepo.getStatusForKjoringMedReguleringsId(regId).any { it.status === Kjoringstatus.FERDIG }
            .let { if (it) throw ReguleringAlreadyFinishedException(regId) }

        val errors = ReguleringValidator.validate(regulering)

        if (errors.flatMap { it.value }.isNotEmpty()) {
            throw IllegalArgumentException(
                "Reguleringsinput er ugyldig, se errors for detaljer",
                IllegalArgumentException(errors.toString()),
            )
        }
        return regulering
    }

    fun deleteReguleringById(
        regId: String
    ): Boolean {
        if (kjoringRepo.getKjoringer().any { it.regulering == regId }) {
            throw IllegalArgumentException("Reguleringen er i bruk av en kjøring og kan ikke slettes")
        }

        return reguleringRepo.deleteReguleringById(regId)
    }
}
