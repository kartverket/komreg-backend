package no.kartverket.komreg.regulering.reform.rules

import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.regulering.reform.format.V1Kinds
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.ReformOp

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

/**
 * En veg under en forelder som utgår, får delvis nøkkel, og det støtter ikke sinken. En egen `Retire` av vegen
 * sletter den, men nye veger skrives før slettingen, så den kan fortsatt avledes fra.
 */
private val deriveNotFromRetired: Check = { scope ->
    scope.ops.filterIsInstance<ReformOp.Derive>().mapNotNull { op ->
        val match = scope.claims.lookup(op.from)
        val retire = match?.operation as? ReformOp.Retire
        if (retire != null && !match.isExact) DomainErr.DeriveFromRetired(op, retire) else null
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
    scope.ops.filterIsInstance<ReformOp.Move>()
        .filter { it.source.hasCadastralNumber() || it.target.hasCadastralNumber() }
        .map { DomainErr.ParcelHasCadastralNumber(it) }
}

/** Teiger uten matrikkelnummer har gnr 0, bnr 0 eller 1, fnr 0 og snr 0. */
private fun Ident.hasCadastralNumber(): Boolean {
    val parts = toArray()
    return (parts[2] as Matrikkelnummer.Gardsnummer).value != 0 ||
        (parts[3] as Matrikkelnummer.Bruksnummer).value !in 0..1 ||
        (parts[4] as Matrikkelnummer.Festenummer).value.toInt() != 0 ||
        (parts[5] as Matrikkelnummer.Seksjonsnummer).value.toInt() != 0
}

/**
 * Kapabilitetstabellen per kind i v1. Kinds som ikke står her, støttes ikke av noen sink. Reglene er utledet fra
 * sinkene i matrikkel-komreg (`no.statkart.matrikkel.komreg.mottaker`), ikke fra DTO-valideringen. Se
 * `docs/reform-format.md` og `docs/reform-format-design.md`.
 */
internal val v1Capabilities: Map<String, SinkCapabilities> = mapOf(
    V1Kinds.COUNTY to SinkCapabilities(
        label = "Fylke",
        actions = setOf(DomainAction.CREATE, DomainAction.DERIVE, DomainAction.RETIRE_TO, DomainAction.RETIRE),
        retire = SuccessorPolicy.FORBIDDEN,
        hints = mapOf(
            DomainAction.MOVE to "Fylke kan ikke flyttes. Gi nytt nummer med `merge` og `create` med `derivedFrom`.",
        ),
        defaultHint = "Bruk `merge` eller `split`.",
        checks = listOf(payloadIs<Fylkesdata>("Fylke", "Fylkesdata")),
    ),
    V1Kinds.MUNICIPALITY to SinkCapabilities(
        label = "Kommune",
        actions = setOf(DomainAction.CREATE, DomainAction.DERIVE, DomainAction.RETIRE_TO, DomainAction.RETIRE),
        retire = SuccessorPolicy.REQUIRED,
        hints = mapOf(
            DomainAction.MOVE to "Kommune kan ikke flyttes. Gi nytt nummer med `merge` og `create` med `derivedFrom`.",
        ),
        defaultHint = "Bruk `merge` eller `split`.",
        checks = listOf(
            payloadIs<Kommunedata>("Kommune", "Kommunedata"),
            municipalityCoat,
        ),
    ),
    V1Kinds.ADDRESS_CODE to SinkCapabilities(
        label = "Veg",
        actions = setOf(DomainAction.DERIVE, DomainAction.MOVE, DomainAction.RETIRE),
        retire = SuccessorPolicy.FORBIDDEN,
        hints = mapOf(
            DomainAction.CREATE to "Veger kan ikke opprettes uten kilde. Flytt en eksisterende veg (`move`), " +
                "eller avled en ny fra den med `create` og `derivedFrom`.",
            // Sinkbegrensning, ikke domeneregel. Se «Bakgrunn for domenereglene» i docs/reform-format-design.md.
            DomainAction.RETIRE_TO to "Sinken støtter ikke sammenslåing av veger ennå. Flytt vegen (`move`), eller " +
                "slett den med `split` uten `successor`, og flytt adressene med egne regler.",
        ),
        defaultHint = "Bruk `move`, `create` med `derivedFrom`, eller `split` uten `successor` for å slette vegen.",
        checks = listOf(deriveNotFromRetired),
    ),
    // Et adressenummer er en serie (nummer med og uten bokstav), som et gårdsnummer. Ved sammenslåing får hver
    // adresse i serien nytt nummer, og sinken oppdaterer den som ved flytting. Like adresser i målet sjekkes ikke.
    V1Kinds.ADDRESS_NUMBER to SinkCapabilities(
        label = "Vegadresse",
        actions = setOf(DomainAction.MOVE, DomainAction.RETIRE_TO),
        defaultHint = "Bare flytting (`move`) og sammenslåing (`merge`) støttes.",
    ),
    V1Kinds.DISTRICT_NUMBER to onlyMove("Krets"),
    V1Kinds.TITLE_NUMBER to onlyMove("Grunneiendom"),
    V1Kinds.LAND_NUMBER to SinkCapabilities(
        label = "Gårdsnummer",
        // Et gårdsnummer er ingen entitet. Ved sammenslåing får hver grunneiendom i serien nytt gårdsnummer, og sinken
        // oppdaterer den som ved flytting. Like bruksnumre i målet sjekkes ikke.
        actions = setOf(DomainAction.MOVE, DomainAction.RETIRE_TO, DomainAction.RETIRE),
        retire = SuccessorPolicy.FORBIDDEN,
        defaultHint = "Flytt gårdsnummeret (`move`), slå det sammen med et annet (`merge`), eller la det utgå " +
            "(`split` uten `successor`) når bruksnumrene under flyttes enkeltvis med `then`.",
        checks = listOf(seriesRetireNeedsTitleMoves),
    ),
    V1Kinds.PARCEL to SinkCapabilities(
        label = "Teig",
        actions = setOf(DomainAction.MOVE),
        defaultHint = ONLY_MOVE,
        checks = listOf(parcelMoves),
    ),
)
