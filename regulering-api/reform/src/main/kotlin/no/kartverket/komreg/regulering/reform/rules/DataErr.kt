package no.kartverket.komreg.regulering.reform.rules

import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.transformation.reform.ReformOp

/**
 * Brudd på en regel som trenger data om fylker og kommuner. [ops] er [ReformOp]-ene feilen gjelder, [rule] er en
 * stabil kode, og [hint] foreslår riktig grep i formatet.
 */
sealed interface DataErr {
    val ops: List<ReformOp>
    val rule: String get() = this::class.simpleName!!
    val message: String
    val hint: String

    /** Et fylke utgår, men en gyldig kommune i fylket har ingen egen kilderegel. */
    data class MunicipalityNotCovered(
        val op: ReformOp.SourceOp,
        val municipality: Ident.NonEmpty,
    ) : DataErr {
        override val ops get() = listOf(op)
        override val message get() = "Kommunen $municipality mangler egen regel når fylket ${op.source} utgår"
        override val hint get() =
            "Legg til en `merge` eller `split` for kommunen i `from[].then` under fylket."
    }

    /** Et mål som må finnes fra før, finnes ikke. */
    data class TargetNotFound(val key: Ident.NonEmpty, override val ops: List<ReformOp>) : DataErr {
        override val message get() = "Målet $key finnes ikke"
        override val hint get() = "Bruk `into.new` for å opprette målet, eller rett nummeret."
    }

    /** Et mål som må finnes fra før, er utgått. */
    data class TargetExpired(val key: Ident.NonEmpty, override val ops: List<ReformOp>) : DataErr {
        override val message get() = "Målet $key er utgått"
        override val hint get() = "Velg et mål som er gyldig, eller opprett et nytt med `into.new`."
    }

    /** Et mål som opprettes, er allerede i bruk av et gyldig fylke eller en gyldig kommune. */
    data class TargetInUse(val key: Ident.NonEmpty, override val ops: List<ReformOp>) : DataErr {
        override val message get() = "Målet $key er allerede i bruk"
        override val hint get() = "Velg et ledig nummer, eller bruk `into.existing` hvis målet skal være det som finnes."
    }

    /** Kilden finnes ikke. */
    data class SourceNotFound(val key: Ident.NonEmpty, override val ops: List<ReformOp>) : DataErr {
        override val message get() = "Kilden $key finnes ikke"
        override val hint get() = "Rett nummeret til kilden."
    }

    /** Kilden er allerede utgått. */
    data class SourceExpired(val key: Ident.NonEmpty, override val ops: List<ReformOp>) : DataErr {
        override val message get() = "Kilden $key er allerede utgått"
        override val hint get() = "Fjern regelen, eller bruk den gyldige etterfølgeren som kilde."
    }
}
