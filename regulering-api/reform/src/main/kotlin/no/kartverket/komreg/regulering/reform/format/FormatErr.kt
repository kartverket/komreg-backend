package no.kartverket.komreg.regulering.reform.format

import kotlinx.serialization.json.JsonPrimitive

/** Konteksten en node står i. Avgjør hvilke op-er som er lov, og om mellomledd i en sti-pivot gir `Keeps`. */
enum class Mode(val description: String, val allowedOps: Set<String>) {
    /** Roten, `adjust.then`, `merge.into.existing.then` og `split.into[].then` med `existing`. Nøkkelen står i ro. */
    KEEP("der nøkkelen står i ro", setOf(OpNames.ADJUST, OpNames.MOVE, OpNames.MERGE, OpNames.SPLIT, OpNames.CREATE)),

    /** `move.then`, `split.then` og `merge.from[].then`. */
    SOURCE("under en kilde", setOf(OpNames.MOVE, OpNames.SPLIT, OpNames.MERGE)),

    /** `create.then`, `merge.into.new.then` og `split.into[].then` med `new`. */
    TARGET("under en ny nøkkel", setOf(OpNames.CREATE)),
}

/** Navnene på op-ene, slik de skrives i JSON. */
object OpNames {
    const val ADJUST = "adjust"
    const val MOVE = "move"
    const val MERGE = "merge"
    const val SPLIT = "split"
    const val CREATE = "create"
}

val OpSpec.opName: String
    get() = when (this) {
        is OpSpec.Adjust -> OpNames.ADJUST
        is OpSpec.Move -> OpNames.MOVE
        is OpSpec.Merge -> OpNames.MERGE
        is OpSpec.Split -> OpNames.SPLIT
        is OpSpec.Create -> OpNames.CREATE
    }

/**
 * Feil i en reform-spec som formatet selv oppdager i resolve. Alle feil har en JSON-sti, en stabil [code] og en
 * norsk [message]. Se `docs/reform-format.md` for alle koder.
 */
sealed interface FormatErr {
    val path: JsonPath
    val code: String
    val message: String

    /** `kind` finnes ikke i segmentregisteret. */
    data class UnknownKind(override val path: JsonPath, val kind: String) : FormatErr {
        override val code get() = "UnknownKind"
        override val message get() = "Ukjent kind «$kind»"
    }

    /** Op-en er ikke lov i konteksten den står i. */
    data class OpNotAllowedInContext(override val path: JsonPath, val op: String, val mode: Mode) : FormatErr {
        override val code get() = "OpNotAllowedInContext"
        override val message get() =
            "$op er ikke lov ${mode.description}. Lovlige op-er her: ${mode.allowedOps.joinToString()}"
    }

    /**
     * Pivoten gjør ikke `kind` til et barn av konteksten: første ledd i stien har en annen forelder enn
     * konteksten. [expected] er antall ledd pivoten skulle hatt, eller `null` hvis `kind` ikke ligger under
     * konteksten i det hele tatt.
     */
    data class KindNotChildOfContext(
        override val path: JsonPath,
        val kind: String,
        val context: String,
        val actual: Int,
        val expected: Int?,
    ) : FormatErr {
        override val code get() = "KindNotChildOfContext"
        override val message get() = when (expected) {
            null -> "$kind ligger ikke under $context"
            else -> "Nøkkelen har $actual ledd, men en $kind under $context skal ha $expected"
        }
    }

    /** Et segment kan ikke dekodes som kinden det står på plassen til. */
    data class InvalidSegment(
        override val path: JsonPath,
        val kind: String,
        val value: JsonPrimitive,
        val reason: String,
    ) : FormatErr {
        override val code get() = "InvalidSegment"
        override val message get() = "Ugyldig verdi $value for $kind: $reason"
    }

    /** Et absolutt mål har ikke like mange segmenter som stien fra roten til `kind`. */
    data class TargetLengthMismatch(
        override val path: JsonPath,
        val kind: String,
        val expected: Int,
        val actual: Int,
    ) : FormatErr {
        override val code get() = "TargetLengthMismatch"
        override val message get() = "Et mål for $kind skal ha $expected segmenter fra roten, men har $actual"
    }

    /** Mer enn én part i en `split` har `role`. */
    data class MultipleRoles(override val path: JsonPath) : FormatErr {
        override val code get() = "MultipleRoles"
        override val message get() = "Høyst én part i en split kan ha role"
    }

    /** En part med `role: identity` har payload. Den arver kilden og skal ikke ha ny data. */
    data class PayloadOnIdentity(override val path: JsonPath) : FormatErr {
        override val code get() = "PayloadOnIdentity"
        override val message get() = "En part med role identity arver kilden og kan ikke ha payload"
    }

    /** En `merge` uten kilder. */
    data class EmptyMerge(override val path: JsonPath) : FormatErr {
        override val code get() = "EmptyMerge"
        override val message get() = "En merge må ha minst én kilde i from"
    }

    /** `merge.into.new` med to eller flere kilder mangler payload. */
    data class PayloadRequired(override val path: JsonPath) : FormatErr {
        override val code get() = "PayloadRequired"
        override val message get() = "Sammenslåing av flere kilder til en ny nøkkel krever payload"
    }

    /** `kind` tar ikke payload. */
    data class PayloadNotAllowed(override val path: JsonPath, val kind: String) : FormatErr {
        override val code get() = "PayloadNotAllowed"
        override val message get() = "$kind tar ikke payload"
    }

    /** Payload kan ikke leses som payload for `kind`. */
    data class InvalidPayload(override val path: JsonPath, val kind: String, val reason: String) : FormatErr {
        override val code get() = "InvalidPayload"
        override val message get() = "Ugyldig payload for $kind: $reason"
    }

    /**
     * En nøkkel står i ro ([path]: `adjust.key`, mellomledd i en sti-pivot i KEEP-kontekst eller
     * `merge.into.existing`), men er samtidig kilde i en annen node ([sourcePath]).
     */
    data class KeptAndConsumed(override val path: JsonPath, val key: String, val sourcePath: JsonPath) : FormatErr {
        override val code get() = "KeptAndConsumed"
        override val message get() =
            "$key står i ro her, men er kilde i $sourcePath. Skriv unntak fra en kilde under kilden."
    }
}
