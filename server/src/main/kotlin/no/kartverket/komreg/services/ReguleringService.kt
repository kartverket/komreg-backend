package no.kartverket.komreg.services

import arrow.core.getOrElse
import io.ktor.server.plugins.NotFoundException
import no.kartverket.komreg.Transformasjonsmotor
import no.kartverket.komreg.exceptions.ReguleringAlreadyFinishedException
import no.kartverket.komreg.exceptions.ReguleringAlreadyRunningException
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.Kjoringstatus
import no.kartverket.komreg.repositories.ReformReguleringRepo
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.routes.Regulering
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.ReformErr
import no.kartverket.komreg.transformation.reform.ReformRegulering
import no.kartverket.komreg.validation.ReguleringValidator

class ReguleringService(
    private val reguleringRepo: ReguleringRepo,
    private val kjoringRepo: KjoringRepo,
    private val reformReguleringRepo: ReformReguleringRepo,
) {

    fun getOrThrowRegulering(
        regId: String,
    ): Regulering {
        val regulering = reguleringRepo.getReguleringById(regId)
            ?: throw NotFoundException("Fant ingen regulering for regId: $regId")

        throwIfRunningOrFinished(regId, Transformasjonsmotor.LEGACY)

        val errors = ReguleringValidator.validate(regulering)

        if (errors.flatMap { it.value }.isNotEmpty()) {
            throw IllegalArgumentException(
                "Reguleringsinput er ugyldig, se errors for detaljer",
                IllegalArgumentException(errors.toString()),
            )
        }
        return regulering
    }

    /**
     * Reglene for reform-reguleringen [regId], validert på nytt med [ReformClaims.validate]. Domene- og
     * datareglene kjøres i `regulering-api` før reglene sendes hit.
     */
    fun getOrThrowReformRegler(regId: String): Kjoringsregler.Reform {
        val regulering = reformReguleringRepo.get(regId)
            ?: throw NotFoundException("Fant ingen reform-regulering for regId: $regId")

        throwIfRunningOrFinished(regId, Transformasjonsmotor.REFORM)

        val claims = ReformClaims.validate(regulering.regler).getOrElse { errs ->
            throw IllegalArgumentException(
                "Reform-reguleringen $regId gir ugyldige regler: ${errs.joinToString { it.message }}",
            )
        }
        return Kjoringsregler.Reform(regId, claims, regulering.ikrafttredelsesdato)
    }

    /** Validerer og lagrer reglene for reform-reguleringen [regId]. */
    fun lagreReformRegulering(regId: String, regulering: ReformRegulering): LagreReformResultat {
        ReformClaims.validate(regulering.regler).onLeft { return LagreReformResultat.Ugyldig(it) }

        if (kjoringRepo.getStatusForKjoringMedReguleringsId(regId, Transformasjonsmotor.REFORM).isNotEmpty()) {
            return LagreReformResultat.IBruk
        }
        return LagreReformResultat.Lagret(opprettet = reformReguleringRepo.upsert(regId, regulering))
    }

    private fun throwIfRunningOrFinished(regId: String, motor: Transformasjonsmotor) {
        val kjoringer = kjoringRepo.getStatusForKjoringMedReguleringsId(regId, motor)
        if (kjoringer.any { it.status === Kjoringstatus.KJØRER }) throw ReguleringAlreadyRunningException(regId)
        if (kjoringer.any { it.status === Kjoringstatus.FERDIG }) throw ReguleringAlreadyFinishedException(regId)
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

sealed interface LagreReformResultat {
    data class Lagret(val opprettet: Boolean) : LagreReformResultat

    data class Ugyldig(val feil: List<ReformErr>) : LagreReformResultat

    /** Reglene kan ikke endres etter at reguleringen er kjørt, siden transformasjonene da ikke stemmer. */
    data object IBruk : LagreReformResultat
}
