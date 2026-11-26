package no.kartverket.komreg.services

import io.ktor.server.plugins.NotFoundException
import no.kartverket.komreg.exceptions.ReguleringAlreadyFinishedException
import no.kartverket.komreg.exceptions.ReguleringAlreadyRunningException
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.Kjoringstatus
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.routes.LegacyRegulering
import no.kartverket.komreg.routes.ReformOpRegulering
import no.kartverket.komreg.routes.Regulering
import no.kartverket.komreg.transformation.reform.ReformClaims
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

        when (regulering) {
            is LegacyRegulering -> {
                val errors = ReguleringValidator.validate(regulering)
                if (errors.flatMap { it.value }.isNotEmpty()) {
                    throw IllegalArgumentException(
                        "Reguleringsinput er ugyldig, se errors for detaljer",
                        IllegalArgumentException(errors.toString()),
                    )
                }
            }
            is ReformOpRegulering -> {
                ReformClaims.validate(regulering.ops).onLeft { errs ->
                    throw IllegalArgumentException(
                        "Reguleringen er ugyldig" + errs.joinToString(
                            "\n\t- ",
                            ":\n\t- ",
                            transform = { it.message }
                        ),
                    )
                }
            }
            else -> throw IllegalStateException("Unexpected regulering type: ${regulering::class.simpleName}")
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
