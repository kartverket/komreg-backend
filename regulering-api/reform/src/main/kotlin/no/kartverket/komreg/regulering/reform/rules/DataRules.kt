package no.kartverket.komreg.regulering.reform.rules

import arrow.core.EitherNel
import arrow.core.left
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.KommuneService
import no.kartverket.komreg.integration.spi.getOrNull
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.ReformOp
import no.kartverket.komreg.transformation.reform.weakTarget

/**
 * Regler som trenger data om fylker og kommuner. Samler alle feil.
 *
 * - Et fylke som utgår må ha en egen, eksakt kilderegel for hver gyldig kommune i fylket.
 * - Fylke- og kommunemål som må finnes fra før ([ReformClaims.requiredExistingTargets]) må finnes og være gyldige.
 * - Fylke- og kommunekilder ([ReformOp.SourceOp.source] og [ReformOp.Derive.from]) må finnes og være gyldige.
 *
 * Andre identtyper sjekkes ikke ennå. [kommuneService] er blokkerende og kalles på [Dispatchers.IO].
 */
suspend fun ReformClaims.validateData(kommuneService: KommuneService): EitherNel<DataErr, Unit> {
    val (fylker, kommuner) = withContext(Dispatchers.IO) {
        kommuneService.findAlleFylker() to kommuneService.findAlleKommuner()
    }
    val fylkeExpired = fylker.associate { it.fylkesnummer to (it.gyldigTilDato != null) }
    val kommuneExpired = kommuner.associate { it.kommunenummer to (it.gyldigTilDato != null) }

    fun status(key: Ident.NonEmpty): Status? = key.countyNumber()?.let {
        fylkeExpired[it].toStatus()
    } ?: key.municipalityNumber()?.let { kommuneExpired[it].toStatus() }

    val coverage = declarations.filterIsInstance<ReformOp.SourceOp>()
        .filter { (it is ReformOp.Retire || it is ReformOp.RetireTo) && it.source.countyNumber() != null }
        .flatMap { op ->
            val county = op.source.countyNumber()!!
            kommuner
                .filter { it.gyldigTilDato == null && it.kommunenummer.fylkesnummer == county }
                .map { it.kommunenummer.toIdent() }
                .filter { lookup(it)?.isExact != true }
                .map { DataErr.MunicipalityNotCovered(op, it) }
        }

    val targets = requiredExistingTargets.flatMap { key ->
        val ops = declarations.filter { it.weakTarget == key }
        when (status(key)) {
            Status.MISSING -> listOf(DataErr.TargetNotFound(key, ops))
            Status.EXPIRED -> listOf(DataErr.TargetExpired(key, ops))
            Status.VALID, null -> emptyList()
        }
    }

    val sources = declarations.mapNotNull { op ->
        when (op) {
            is ReformOp.SourceOp -> op.source
            is ReformOp.Derive -> op.from
            is ReformOp.Create -> null
        }?.let { it to op }
    }.groupBy({ it.first }, { it.second }).flatMap { (key, ops) ->
        when (status(key)) {
            Status.MISSING -> listOf(DataErr.SourceNotFound(key, ops))
            Status.EXPIRED -> listOf(DataErr.SourceExpired(key, ops))
            Status.VALID, null -> emptyList()
        }
    }

    return (coverage + targets + sources).toNonEmptyListOrNull()?.left() ?: Unit.right()
}

private enum class Status { VALID, EXPIRED, MISSING }

private fun Boolean?.toStatus(): Status = when (this) {
    null -> Status.MISSING
    true -> Status.EXPIRED
    false -> Status.VALID
}

private fun Ident.NonEmpty.countyNumber(): Fylkesnummer? = if (size == 1) getOrNull<Fylkesnummer>() else null

private fun Ident.NonEmpty.municipalityNumber(): Kommunenummer? {
    if (size != 2) return null
    val county = getOrNull<Fylkesnummer>() ?: return null
    val serial = getOrNull<Kommunenummer.Lopenummer>() ?: return null
    return Kommunenummer(county, serial)
}

private fun Kommunenummer.toIdent(): Ident.NonEmpty = Kommune.KommuneIdent(fylkesnummer, lopenummer)
