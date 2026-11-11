package no.kartverket.komreg.transformation.reform

import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Payload

/**
 * En regel i en reform (regulering), uttrykt som eierskap til nøkler (`Ident`).
 *
 * ## Fire grunnhandlinger
 *
 * | Handling   | Operasjoner                                  | Hva skjer                                              |
 * |------------|----------------------------------------------|--------------------------------------------------------|
 * | Opprett    | [Create]                                     | Ny entitet fra ingenting.                              |
 * | Flytt      | [Move]                                       | Samme entitet, ny nøkkel. ID og etterkommere følger.   |
 * | Sammenslå  | [RetireTo]                                   | Kilden utgår og peker på målet. Etterkommere følger.   |
 * | Del        | [Retire] + én [Derive] per del               | Kilden utgår, og hver del blir en ny entitet.          |
 *
 * Eksempler:
 * - Vegen flyttes til ny kommune: `move(vegA, vegB)`.
 * - Kommune A og B slås sammen til C: `retireTo(A, C)` og `retireTo(B, C)`, og C opprettes via [Create]
 *   (eller finnes fra før).
 * - Kommune A skifter nummer til A': `retireTo(A, A')` og `derive(A', from = A)`.
 * - Kommune A deles i B og C: `retire(A, successor = B)`, `derive(B, from = A)` og `derive(C, from = A)`.
 *
 * ## Under panseret
 *
 * `Id` er surrogatnøkkel. Kun [NewEntity] ([Create] og [Derive]) allokerer nye ID-er; de andre operasjonene
 * endrer bare forretningsnøkkelen. Regler for kilder ([SourceOp]) slås opp langs forelderkjeden, så en regel
 * for en nøkkel gjelder også etterkommerne som ikke har en mer spesifikk regel.
 */
sealed interface ReformOp {
    /** Ny entitet med ny ID og nøkkel [target]. Eier målet eksklusivt. */
    sealed interface NewEntity : ReformOp {
        val target: Ident.NonEmpty
    }

    /**
     * **Opprett**: ny entitet uten kildeentitet. All data til den nye entiteten må ligge i [payload].
     * Materialiseres på forhånd, én gang per registrert `IdType` for målets identtype.
     */
    data class Create(override val target: Ident.NonEmpty, val payload: Payload) : NewEntity

    /**
     * **Del** (en del av delingen): ny entitet avledet fra hver entitet med nøkkel [from]: samme `IdType`,
     * kildeentiteten som kilde og kildens transformerte assosierte identer. Bestemmer ikke skjebnen til [from]
     * og flytter ingen etterkommere; det gjør en [SourceOp] for [from] ([Retire] eller [RetireTo]).
     */
    data class Derive(
        override val target: Ident.NonEmpty,
        val from: Ident.NonEmpty,
        val payload: Payload? = null,
    ) : NewEntity

    /** Operasjon som bestemmer skjebnen til en eksisterende nøkkel. Maks én per kilde. */
    sealed interface SourceOp : ReformOp {
        val source: Ident.NonEmpty
    }

    /**
     * **Flytt**: nøkkelen [source] blir [target]. Entiteten beholder ID-en, og etterkommerne følger med.
     * Eier kilde og mål eksklusivt.
     */
    data class Move(override val source: Ident.NonEmpty, val target: Ident.NonEmpty) : SourceOp

    /**
     * **Del**: kilden utgår fordi den er delt opp i flere deler. Delene opprettes med [Derive] fra [source].
     *
     * [successor] er den ene delen som kilden og referanser til den peker på. Kilden kan bare peke på ett mål,
     * så den velges blant delene (kommuner: første mål). Fylker har ingen etterfølger. Pekeren er svak
     * (delbar); delene eies av sine [Derive].
     *
     * Etterkommere uten egen regel får delvis nøkkel (kun umatchede barnekomponenter), siden det ikke finnes
     * noe entydig sted de kan følge med til. Sinkene avgjør hva det å utgå betyr. Eier kilden.
     */
    data class Retire(override val source: Ident.NonEmpty, val successor: Ident.NonEmpty? = null) : SourceOp

    /**
     * **Sammenslå** (og 1:1-erstatning): kilden utgår og peker på [target], uten ny ID. Etterkommere uten egen
     * regel følger til [target]. Flere kilder kan peke på samme mål.
     *
     * Hvis målet er avledet fra kilden ([Derive]), er det en 1:1-erstatning og ikke en sammenslåing. Ellers
     * må målet finnes fra før eller opprettes med [Create]. Eier kilden, og har en svak (delbar) referanse til
     * målet.
     */
    data class RetireTo(override val source: Ident.NonEmpty, val target: Ident.NonEmpty) : SourceOp
}

class ReformBuilder {
    private val ops = mutableListOf<ReformOp>()

    fun create(target: Ident.NonEmpty, payload: Payload) {
        ops += ReformOp.Create(target, payload)
    }

    fun derive(target: Ident.NonEmpty, from: Ident.NonEmpty, payload: Payload? = null) {
        ops += ReformOp.Derive(target, from, payload)
    }

    fun move(source: Ident.NonEmpty, target: Ident.NonEmpty) {
        ops += ReformOp.Move(source, target)
    }

    fun retire(source: Ident.NonEmpty, successor: Ident.NonEmpty? = null) {
        ops += ReformOp.Retire(source, successor)
    }

    fun retireTo(source: Ident.NonEmpty, target: Ident.NonEmpty) {
        ops += ReformOp.RetireTo(source, target)
    }

    @PublishedApi
    internal fun build(): List<ReformOp> = ops.toList()
}

inline fun reform(block: ReformBuilder.() -> Unit): List<ReformOp> = ReformBuilder().apply(block).build()

/** Eksklusivt mål for operasjonen, hvis den har et. */
val ReformOp.exclusiveTarget: Ident.NonEmpty?
    get() = when (this) {
        is ReformOp.NewEntity -> target
        is ReformOp.Move -> target
        is ReformOp.Retire, is ReformOp.RetireTo -> null
    }

/** Svakt (delbart) mål for operasjonen, hvis den har et. */
val ReformOp.weakTarget: Ident.NonEmpty?
    get() = when (this) {
        is ReformOp.RetireTo -> target
        is ReformOp.Retire -> successor
        is ReformOp.NewEntity, is ReformOp.Move -> null
    }

/** Operasjoner som allokerer ny ID for målet. */
val ReformOp.createsTarget: Boolean
    get() = this is ReformOp.NewEntity
