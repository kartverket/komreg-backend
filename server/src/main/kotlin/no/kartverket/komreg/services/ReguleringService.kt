package no.kartverket.komreg.services

import arrow.core.getOrElse
import io.ktor.server.plugins.NotFoundException
import no.kartverket.komreg.Transformasjonsmotor
import no.kartverket.komreg.exceptions.ReguleringAlreadyFinishedException
import no.kartverket.komreg.exceptions.ReguleringAlreadyRunningException
import no.kartverket.komreg.repositories.Kjoring
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.Kjoringstatus
import no.kartverket.komreg.repositories.ReformReguleringRepo
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.repositories.iTransaksjon
import no.kartverket.komreg.routes.Regulering
import no.kartverket.komreg.routes.toReguleringsinput
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.ReformErr
import no.kartverket.komreg.transformation.reform.ReformRegulering
import no.kartverket.komreg.validation.ReguleringValidator
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

class ReguleringService(
    private val dataSource: DataSource,
    private val reguleringRepo: ReguleringRepo,
    private val kjoringRepo: KjoringRepo,
    private val reformReguleringRepo: ReformReguleringRepo,
) {

    fun getOrThrowRegulering(
        regId: String,
    ): Regulering {
        val regulering = reguleringRepo.getReguleringById(regId)
            ?: throw NotFoundException("Fant ingen regulering for regId: $regId")

        throwIfRunningOrFinished(kjoringRepo.getStatusForKjoringMedReguleringsId(regId, Transformasjonsmotor.LEGACY), regId)

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
     * Henter reglene for [regId] og gjenopptar en stoppet kjøring eller oppretter en ny.
     *
     * For `reform` skjer lesing av reglene og opprettelse av kjøringen i samme transaksjon, med raden i
     * `reform_regulering` låst, slik at [lagreReformRegulering] ikke kan endre reglene i mellomtiden.
     */
    fun startKjoring(regId: String, motor: Transformasjonsmotor): StartetKjoring = when (motor) {
        Transformasjonsmotor.LEGACY -> {
            val regler = Kjoringsregler.Legacy(getOrThrowRegulering(regId).toReguleringsinput())
            dataSource.iTransaksjon { opprettEllerGjenopptaKjoring(it, regId, motor, regler) }
        }

        Transformasjonsmotor.REFORM -> dataSource.iTransaksjon { connection ->
            opprettEllerGjenopptaKjoring(connection, regId, motor, getOrThrowReformRegler(connection, regId))
        }
    }

    private fun opprettEllerGjenopptaKjoring(
        connection: Connection,
        regId: String,
        motor: Transformasjonsmotor,
        regler: Kjoringsregler,
    ): StartetKjoring {
        val stoppet = kjoringRepo.finnStoppetKjøringForRegulering(connection, regId, motor)
        if (stoppet != null) {
            // Kreves i samme transaksjon, slik at en samtidig forespørsel ikke gjenopptar den samme kjøringen.
            kjoringRepo.setStatusForKjøring(connection, stoppet.id, Kjoringstatus.KJØRER)
            return StartetKjoring(regler, stoppet.id, gjenopptatt = true)
        }

        val kjoringId = kjoringRepo.insertAndRetrieveKjoringId(connection, regId, motor)
            ?: throw SQLException("Kunne ikke opprette kjøring for regId: $regId")
        return StartetKjoring(regler, kjoringId, gjenopptatt = false)
    }

    /**
     * Reglene for reform-reguleringen [regId], validert på nytt med [ReformClaims.validate]. Domene- og
     * datareglene kjøres i `regulering-api` før reglene sendes hit. Raden låses ut transaksjonen på [connection].
     */
    private fun getOrThrowReformRegler(connection: Connection, regId: String): Kjoringsregler.Reform {
        val regulering = reformReguleringRepo.getForUpdate(connection, regId)
            ?: throw NotFoundException("Fant ingen reform-regulering for regId: $regId")

        throwIfRunningOrFinished(
            kjoringRepo.getStatusForKjoringMedReguleringsId(connection, regId, Transformasjonsmotor.REFORM),
            regId,
        )

        val claims = ReformClaims.validate(regulering.regler).getOrElse { errs ->
            throw IllegalArgumentException(
                "Reform-reguleringen $regId gir ugyldige regler: ${errs.joinToString { it.message }}",
            )
        }
        return Kjoringsregler.Reform(regId, claims, regulering.ikrafttredelsesdato)
    }

    /**
     * Validerer og lagrer reglene for reform-reguleringen [regId]. Sjekken mot kjøringer og lagringen skjer
     * i samme transaksjon som [startKjoring] låser, så reglene kan ikke endres etter at en kjøring har lest dem.
     */
    fun lagreReformRegulering(regId: String, regulering: ReformRegulering): LagreReformResultat {
        ReformClaims.validate(regulering.regler).onLeft { return LagreReformResultat.Ugyldig(it) }

        return dataSource.iTransaksjon { connection ->
            reformReguleringRepo.lock(connection, regId)
            if (kjoringRepo.getStatusForKjoringMedReguleringsId(connection, regId, Transformasjonsmotor.REFORM).isNotEmpty()) {
                LagreReformResultat.IBruk
            } else {
                LagreReformResultat.Lagret(opprettet = reformReguleringRepo.upsert(connection, regId, regulering))
            }
        }
    }

    private fun throwIfRunningOrFinished(kjoringer: List<Kjoring>, regId: String) {
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

/** En kjøring som er klar til å transformeres med [regler]. */
data class StartetKjoring(val regler: Kjoringsregler, val kjoringId: Int, val gjenopptatt: Boolean)

sealed interface LagreReformResultat {
    data class Lagret(val opprettet: Boolean) : LagreReformResultat

    data class Ugyldig(val feil: List<ReformErr>) : LagreReformResultat

    /** Reglene kan ikke endres etter at reguleringen er kjørt, siden transformasjonene da ikke stemmer. */
    data object IBruk : LagreReformResultat
}
