package no.kartverket.komreg.services

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respond
import no.kartverket.komreg.exceptions.ReguleringAlreadyFinishedException
import no.kartverket.komreg.exceptions.ReguleringAlreadyRunningException
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.Kjoringstatus
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.routes.Regulering
import no.kartverket.komreg.validation.ReguleringValidator

fun getOrThrowRegulering(reguleringRepo: ReguleringRepo,
                         kjoringRepo: KjoringRepo,
                         regId: String): Regulering {
    val regulering = reguleringRepo.getReguleringById(regId)
        ?: throw NotFoundException("Fant ingen regulering for regId: $regId")

    val errors = ReguleringValidator.validate(regulering)
    if (errors.flatMap { it.value }.isNotEmpty()) {
        throw IllegalArgumentException("Reguleringsinput er ugyldig, se errors for detaljer", IllegalArgumentException(errors.toString()))
    }

    if(kjoringRepo.getStatusForKjoringMedReguleringsId(regId).any { it.status === Kjoringstatus.KJØRER }
            .also { if (it) throw ReguleringAlreadyRunningException(regId) }
        )

    kjoringRepo.getStatusForKjoringMedReguleringsId(regId).any { it.status === Kjoringstatus.FERDIG }
        .also { if (it) throw ReguleringAlreadyFinishedException(regId) }
    return regulering
}