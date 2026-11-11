package no.kartverket.komreg.regulering.reform.rules

import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.regulering.reform.format.V1Kinds
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.ReformOp
import no.kartverket.komreg.transformation.reform.exclusiveTarget
import no.kartverket.komreg.transformation.reform.weakTarget

/** Om en [ReformOp.Retire] skal ha etterfølger. */
internal enum class SuccessorPolicy { REQUIRED, FORBIDDEN }

/** Operasjonene av én identtype, med claims for oppslag på tvers av identtyper. */
internal class RuleScope(val claims: ReformClaims, val ops: List<ReformOp>)

internal typealias Check = (RuleScope) -> List<DomainErr>

/**
 * Hva sinkene støtter for én identtype.
 *
 * @param actions handlingene identtypen støtter.
 * @param retire om [DomainAction.RETIRE] har etterfølger. Bare relevant når [DomainAction.RETIRE] er støttet.
 * @param hints hva en bruker bør gjøre i stedet, per handling som ikke støttes.
 * @param checks tilleggskrav til operasjonene som er støttet.
 */
internal class SinkCapabilities(
    val label: String,
    val actions: Set<DomainAction>,
    val retire: SuccessorPolicy? = null,
    val hints: Map<DomainAction, String> = emptyMap(),
    val defaultHint: String,
    val checks: List<Check> = emptyList(),
)

private const val ONLY_MOVE = "Bare flytting (`move`) støttes."

private fun onlyMove(label: String) = SinkCapabilities(
    label = label,
    actions = setOf(DomainAction.MOVE),
    defaultHint = ONLY_MOVE,
)

private inline fun <reified P : Any> payloadIs(label: String, expected: String): Check = { scope ->
    scope.ops.filterIsInstance<ReformOp.NewEntity>().mapNotNull { op ->
        val payload = when (op) {
            is ReformOp.Create -> op.payload
            is ReformOp.Derive -> op.payload
        }
        if (payload is P) null else DomainErr.WrongPayload(op, label, expected)
    }
}

private val municipalityCoat: Check = { scope ->
    scope.ops.filterIsInstance<ReformOp.NewEntity>().mapNotNull { op ->
        val payload = when (op) {
            is ReformOp.Create -> op.payload
            is ReformOp.Derive -> op.payload
        }
        if (payload is Kommunedata && payload.kommunevapen == null) DomainErr.MissingMunicipalityCoat(op) else null
    }
}

private val successorIsDerived: Check = { scope ->
    scope.ops.filterIsInstance<ReformOp.Retire>().mapNotNull { op ->
        val successor = op.successor
        val derivedTargets = scope.claims.derives[op.source].orEmpty().map { it.target }
        if (successor != null && successor !in derivedTargets) DomainErr.SuccessorNotDerived(op, successor) else null
    }
}

private val targetsInOneCounty: Check = { scope ->
    val sourceOps = scope.ops.filterIsInstance<ReformOp.SourceOp>().associateBy { it.source }
    val derives = scope.ops.filterIsInstance<ReformOp.Derive>().map { it.from }
    (sourceOps.keys + derives).mapNotNull { source ->
        val sourceOp = sourceOps[source]
        val derived = scope.claims.derives[source].orEmpty()
        val targets = listOfNotNull(sourceOp?.let { it.exclusiveTarget ?: it.weakTarget }) + derived.map { it.target }
        if (targets.map { it.county() }.distinct().size > 1) {
            DomainErr.TargetsInMultipleCounties(source, listOfNotNull(sourceOp) + derived)
        } else {
            null
        }
    }
}

private val deriveOnlyFromMove: Check = { scope ->
    scope.ops.filterIsInstance<ReformOp.Derive>().mapNotNull { op ->
        val rule = scope.claims.lookup(op.from)?.takeIf { it.isExact }?.operation
        if (rule is ReformOp.Move) null else DomainErr.DeriveWithoutMove(op)
    }
}

private val seriesRetireNeedsTitleMoves: Check = { scope ->
    val titleMoves = scope.claims.declarations.filterIsInstance<ReformOp.Move>()
        .map { it.source.dropLast() }
        .toSet()
    scope.ops.filterIsInstance<ReformOp.Retire>().mapNotNull { op ->
        if (op.source in titleMoves) null else DomainErr.SeriesRetireWithoutTitleMoves(op)
    }
}

private val parcelMoves: Check = { scope ->
    val moves = scope.ops.filterIsInstance<ReformOp.Move>()
    val (withNumber, plain) = moves.partition { it.source.hasCadastralNumber() || it.target.hasCadastralNumber() }

    val unpaired = plain.groupBy { it.source.parcel() to it.target.parcel() }.values.mapNotNull { group ->
        val pairs = group.map { it.source.leaseRole() to it.target.leaseRole() }.toSet()
        if (pairs == setOf(0 to 0, 1 to 1)) null else DomainErr.ParcelNotPaired(group.first().source, group)
    }
    withNumber.map { DomainErr.ParcelHasCadastralNumber(it) } + unpaired
}

private fun Ident.county(): Comparable<*> = toArray().first()

private fun Ident.parcel(): List<Comparable<*>> = toArray().let { it.take(2) + it[6] }

private fun Ident.leaseRole(): Int = (toArray()[3] as Matrikkelnummer.Bruksnummer).value.toInt()

/** Teiger uten matrikkelnummer har gnr 0, bnr 0 eller 1, fnr 0 og snr 0. */
private fun Ident.hasCadastralNumber(): Boolean {
    val parts = toArray()
    return (parts[2] as Matrikkelnummer.Gardsnummer).value != 0 ||
        (parts[3] as Matrikkelnummer.Bruksnummer).value !in 0..1 ||
        (parts[4] as Matrikkelnummer.Festenummer).value.toInt() != 0 ||
        (parts[5] as Matrikkelnummer.Seksjonsnummer).value.toInt() != 0
}

/** Kapabilitetstabellen per kind i v1. Kinds som ikke står her, støttes ikke av noen sink. */
internal val v1Capabilities: Map<String, SinkCapabilities> = mapOf(
    V1Kinds.COUNTY to SinkCapabilities(
        label = "Fylke",
        actions = setOf(DomainAction.CREATE, DomainAction.DERIVE, DomainAction.RETIRE_TO, DomainAction.RETIRE),
        retire = SuccessorPolicy.FORBIDDEN,
        hints = mapOf(
            DomainAction.MOVE to "Fylke kan ikke flyttes. Bruk `merge` med `into.new` for renummerering.",
        ),
        defaultHint = "Bruk `merge` eller `split`.",
        checks = listOf(payloadIs<Fylkesdata>("Fylke", "Fylkesdata")),
    ),
    V1Kinds.MUNICIPALITY to SinkCapabilities(
        label = "Kommune",
        actions = setOf(DomainAction.CREATE, DomainAction.DERIVE, DomainAction.RETIRE_TO, DomainAction.RETIRE),
        retire = SuccessorPolicy.REQUIRED,
        hints = mapOf(
            DomainAction.MOVE to "Kommune kan ikke flyttes. Bruk `merge` med `into.new` for renummerering.",
        ),
        defaultHint = "Bruk `merge` eller `split`.",
        checks = listOf(
            payloadIs<Kommunedata>("Kommune", "Kommunedata"),
            municipalityCoat,
            successorIsDerived,
            targetsInOneCounty,
        ),
    ),
    V1Kinds.ADDRESS_CODE to SinkCapabilities(
        label = "Veg",
        actions = setOf(DomainAction.DERIVE, DomainAction.MOVE),
        hints = mapOf(
            DomainAction.CREATE to "Veger kan ikke opprettes. Flytt en eksisterende veg (`move`), " +
                "eller del den med `split` der én av delene har `role: identity`.",
            DomainAction.RETIRE_TO to "Veger kan ikke avvikles. Bruk `split` der én av delene har `role: identity`.",
            DomainAction.RETIRE to "Veger kan ikke avvikles, det sletter vegen. " +
                "Bruk `split` der én av delene har `role: identity`.",
        ),
        defaultHint = "Bruk `move`, eller `split` der én av delene har `role: identity`.",
        checks = listOf(deriveOnlyFromMove, targetsInOneCounty),
    ),
    V1Kinds.ADDRESS_NUMBER to onlyMove("Vegadresse"),
    V1Kinds.DISTRICT_NUMBER to onlyMove("Krets"),
    V1Kinds.TITLE_NUMBER to onlyMove("Grunneiendom"),
    V1Kinds.LAND_NUMBER to SinkCapabilities(
        label = "Gårdsnummer",
        actions = setOf(DomainAction.MOVE, DomainAction.RETIRE),
        retire = SuccessorPolicy.FORBIDDEN,
        defaultHint = "Flytt gårdsnummeret (`move`), eller la det utgå (`split` med tom `into`) " +
            "når bruksnumrene under flyttes enkeltvis med `then`.",
        checks = listOf(seriesRetireNeedsTitleMoves),
    ),
    V1Kinds.PARCEL to SinkCapabilities(
        label = "Teig",
        actions = setOf(DomainAction.MOVE),
        defaultHint = ONLY_MOVE,
        checks = listOf(parcelMoves),
    ),
)
