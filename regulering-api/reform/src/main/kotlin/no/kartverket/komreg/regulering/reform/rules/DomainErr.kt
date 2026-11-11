package no.kartverket.komreg.regulering.reform.rules

import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.transformation.reform.ReformOp

/** Hva en [ReformOp] gjør, slik sinkkapabilitetene deles inn. */
enum class DomainAction(val verb: String) {
    CREATE("opprettes"),
    DERIVE("avledes"),
    MOVE("flyttes"),
    RETIRE_TO("slås sammen"),
    RETIRE("utgå"),
}

/**
 * Brudd på en regel for hva sinkene og domenet støtter. [ops] er [ReformOp]-ene feilen gjelder, [rule] er en
 * stabil kode, og [hint] foreslår riktig grep i formatet.
 */
sealed interface DomainErr {
    val ops: List<ReformOp>
    val rule: String get() = this::class.simpleName!!
    val message: String
    val hint: String

    /** Identtypen finnes ikke i registeret, eller har ingen sink som støtter reformer. */
    data class UnsupportedIdentType(val op: ReformOp, val key: Ident.NonEmpty) : DomainErr {
        override val ops get() = listOf(op)
        override val message get() = "Nøkkelen $key har en identtype som reformer ikke støtter"
        override val hint get() =
            "Bruk fylke, kommune, veg, vegadresse, krets, gårdsnummer, bruksnummer eller teig."
    }

    /** Identtypen støtter ikke handlingen. */
    data class ActionNotSupported(
        val op: ReformOp,
        val label: String,
        val action: DomainAction,
        override val hint: String,
    ) : DomainErr {
        override val ops get() = listOf(op)
        override val message get() = "$label kan ikke ${action.verb}: $op"
    }

    /** Målet (eller etterfølgeren) har en annen identtype enn kilden. */
    data class TargetTypeMismatch(val op: ReformOp.SourceOp, val target: Ident.NonEmpty) : DomainErr {
        override val ops get() = listOf(op)
        override val message get() = "Målet $target har ikke samme identtype som kilden ${op.source}"
        override val hint get() = "Kilde og mål må ha samme kind, for eksempel kommune til kommune."
    }

    /** Payloaden har feil type for identtypen, eller mangler. */
    data class WrongPayload(val op: ReformOp.NewEntity, val label: String, val expected: String) : DomainErr {
        override val ops get() = listOf(op)
        override val message get() = "$label som opprettes må ha $expected, men har ${op.payloadName()}"
        override val hint get() = "Legg til `payload` for $label."
    }

    /** En ny kommune mangler kommunevåpen. */
    data class MissingMunicipalityCoat(val op: ReformOp.NewEntity) : DomainErr {
        override val ops get() = listOf(op)
        override val message get() = "Kommunen ${op.target} mangler kommunevåpen"
        override val hint get() = "Legg til `kommunevapen` (base64) i payloaden til kommunen."
    }

    /** Kilden utgår uten etterfølger, selv om identtypen krever en. */
    data class RetireNeedsSuccessor(val op: ReformOp.Retire, val label: String) : DomainErr {
        override val ops get() = listOf(op)
        override val message get() = "$label som utgår må ha en etterfølger: $op"
        override val hint get() = "Bruk `split` og marker én av delene med `role: successor`."
    }

    /** Kilden utgår med etterfølger, selv om identtypen ikke har noen. */
    data class RetireHasSuccessor(val op: ReformOp.Retire, val label: String) : DomainErr {
        override val ops get() = listOf(op)
        override val message get() = "$label som utgår kan ikke ha en etterfølger: $op"
        override val hint get() = "Fjern `role: successor` fra delingen."
    }

    /** Etterfølgeren er ikke et [ReformOp.Derive]-mål fra samme kilde. */
    data class SuccessorNotDerived(val op: ReformOp.Retire, val successor: Ident.NonEmpty) : DomainErr {
        override val ops get() = listOf(op)
        override val message get() = "Etterfølgeren $successor er ikke avledet fra kilden ${op.source}"
        override val hint get() = "Etterfølgeren må være en av delene i samme `split` som kilden."
    }

    /** Målene fra én kilde ligger i flere fylker. */
    data class TargetsInMultipleCounties(val source: Ident.NonEmpty, override val ops: List<ReformOp>) : DomainErr {
        override val message get() = "Målene fra $source ligger i flere fylker"
        override val hint get() = "Alle deler av en deling må ligge i samme fylke. Del i ett fylke om gangen."
    }

    /** Veg avledes fra en kilde som ikke flyttes. */
    data class DeriveWithoutMove(val op: ReformOp.Derive) : DomainErr {
        override val ops get() = listOf(op)
        override val message get() = "Vegen ${op.from} avledes uten å flyttes: $op"
        override val hint get() = "Bruk `split` der én av delene har `role: identity`, slik at vegen beholder ID-en."
    }

    /** Gårdsnummerserie utgår uten at bruksnumrene under flyttes. */
    data class SeriesRetireWithoutTitleMoves(val op: ReformOp.Retire) : DomainErr {
        override val ops get() = listOf(op)
        override val message get() = "Gårdsnummeret ${op.source} utgår uten at noen bruksnumre under flyttes"
        override val hint get() = "Legg til `then` med `move` av hvert bruksnummer, eller flytt gårdsnummeret med `move`."
    }

    /** Teigen har matrikkelnummer. */
    data class ParcelHasCadastralNumber(val op: ReformOp.Move) : DomainErr {
        override val ops get() = listOf(op)
        override val message get() = "Teigen i $op har matrikkelnummer"
        override val hint get() = "Bare teiger uten matrikkelnummer (gnr 0, fnr 0, snr 0) kan flyttes."
    }

    /** Teigen flyttes ikke for både bnr 0 og bnr 1 til samme mål. */
    data class ParcelNotPaired(val source: Ident.NonEmpty, override val ops: List<ReformOp.Move>) : DomainErr {
        override val message get() = "Teigen $source må flyttes for både bnr 0 og bnr 1 til samme mål"
        override val hint get() = "Flytt teigen med `move` på kind `parcel`, som gjelder begge."
    }
}

private fun ReformOp.NewEntity.payloadName(): String = when (this) {
    is ReformOp.Create -> payload::class.simpleName
    is ReformOp.Derive -> payload?.let { it::class.simpleName } ?: "ingen payload"
} ?: "ukjent payload"
