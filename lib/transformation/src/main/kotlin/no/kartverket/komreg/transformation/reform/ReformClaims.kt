package no.kartverket.komreg.transformation.reform

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.transformation.reform.ReformOp.Create
import no.kartverket.komreg.transformation.reform.ReformOp.Derive
import no.kartverket.komreg.transformation.reform.ReformOp.SourceOp

sealed interface ReformErr {
    val message: String

    /** Samme kilde har flere regler. */
    data class Diverges(val source: Ident.NonEmpty, val ops: List<SourceOp>) : ReformErr {
        override val message get() = "Kilden $source har flere regler: $ops"
    }

    /** Et eksklusivt mål har flere eiere. */
    data class Conflict(val target: Ident.NonEmpty, val ops: List<ReformOp>) : ReformErr {
        override val message get() = "Målet $target eies av flere regler: $ops"
    }

    /** Nøkkelen er både kilde og (eksklusivt eller svakt) mål. */
    data class SourceIsTarget(
        val key: Ident.NonEmpty,
        val sourceOps: List<SourceOp>,
        val targetOps: List<ReformOp>,
    ) : ReformErr {
        override val message get() = "Nøkkelen $key er både kilde i $sourceOps og mål i $targetOps"
    }

    /** [Derive.from] er et eksklusivt mål (også sitt eget), og finnes dermed ikke før reformen. */
    data class DeriveFromTarget(val derive: Derive, val targetOps: List<ReformOp>) : ReformErr {
        override val message get() = "Avledningen $derive er fra ${derive.from}, som er mål i $targetOps"
    }

    /** [Derive.from] og [Derive.target] har ulik identtype. */
    data class DeriveTypeMismatch(val derive: Derive) : ReformErr {
        override val message get() =
            "Avledningen $derive har ulik identtype på kilde (${derive.from.type}) og mål (${derive.target.type})"
    }

    /** Et mål ligger under en kilde, og ville dermed selv blitt flyttet/utgått. */
    data class TargetUnderSource(
        val target: Ident.NonEmpty,
        val targetOp: ReformOp,
        val source: Ident.NonEmpty,
        val sourceOps: List<SourceOp>,
    ) : ReformErr {
        override val message get() = "Målet $target i $targetOp ligger under kilden $source i $sourceOps"
    }

    /**
     * En kilde (eller [Derive.from]) ligger under et mål som opprettes, og kan derfor aldri treffes (død regel).
     */
    data class SourceUnderCreatedTarget(
        val source: Ident.NonEmpty,
        val sourceOp: ReformOp,
        val createdTarget: Ident.NonEmpty,
        val createOp: ReformOp,
    ) : ReformErr {
        override val message get() = "Kilden $source i $sourceOp ligger under $createdTarget, som opprettes av $createOp"
    }
}

/**
 * Treff på en regel. [matchedSize] er antall komponenter i nøkkelen regelen gjelder for,
 * [unmatchedSize] er antall barnekomponenter i oppslagsnøkkelen under den.
 */
data class ReformMatch(val operation: SourceOp, val matchedSize: Int, val unmatchedSize: Int) {
    val isExact: Boolean get() = unmatchedSize == 0
}

/** Validert sett med [ReformOp]-er, indeksert på kilde og mål. */
class ReformClaims private constructor(
    private val sourceClaims: Map<Ident.NonEmpty, SourceOp>,
    private val targetClaims: Map<Ident.NonEmpty, ReformOp>,
    private val weakTargetClaims: Map<Ident.NonEmpty, Set<SourceOp>>,
    val declarations: List<ReformOp>,
) {
    /** Mest spesifikke regel for [key]: hele nøkkelen først, deretter foreldrene. */
    fun lookup(key: Ident.NonEmpty): ReformMatch? =
        key.ancestorsAndSelf().firstNotNullOfOrNull { ancestor ->
            sourceClaims[ancestor]?.let { ReformMatch(it, ancestor.size, key.size - ancestor.size) }
        }

    /** Svake mål ([ReformOp.weakTarget]) som ikke opprettes eller flyttes hit i reformen, og derfor må finnes fra før. */
    val requiredExistingTargets: Set<Ident.NonEmpty> = weakTargetClaims.keys - targetClaims.keys

    val creates: List<Create> = declarations.filterIsInstance<Create>()

    /** [Derive]-ene gruppert på [Derive.from], i deklarasjonsrekkefølge. */
    val derives: Map<Ident.NonEmpty, List<Derive>> = declarations.filterIsInstance<Derive>().groupBy { it.from }

    companion object {
        /**
         * Validerer [ops] og samler alle feil. Identiske duplikater regnes som én regel.
         */
        fun validate(ops: List<ReformOp>): Either<NonEmptyList<ReformErr>, ReformClaims> {
            val declarations = ops.distinct()

            val sources = declarations.filterIsInstance<SourceOp>().groupBy { it.source }
            val targets = declarations
                .mapNotNull { op -> op.exclusiveTarget?.let { it to op } }
                .groupBy({ it.first }, { it.second })
            val weakTargets = declarations.filterIsInstance<SourceOp>()
                .mapNotNull { op -> op.weakTarget?.let { it to op } }
                .groupBy({ it.first }, { it.second })
            val derives = declarations.filterIsInstance<Derive>()

            val errors = buildList {
                sources.forEach { (source, sourceOps) ->
                    if (sourceOps.size > 1) add(ReformErr.Diverges(source, sourceOps))
                }

                targets.forEach { (target, targetOps) ->
                    if (targetOps.size > 1) add(ReformErr.Conflict(target, targetOps))
                }

                sources.forEach { (key, sourceOps) ->
                    val targetOps = targets[key].orEmpty() + weakTargets[key].orEmpty()
                    if (targetOps.isNotEmpty()) add(ReformErr.SourceIsTarget(key, sourceOps, targetOps))
                }

                val allTargets = targets.flatMap { (target, ops) -> ops.map { target to it } } +
                    weakTargets.flatMap { (target, ops) -> ops.map { target to it } }
                allTargets.forEach { (target, targetOp) ->
                    target.strictAncestors().forEach { ancestor ->
                        sources[ancestor]?.let { add(ReformErr.TargetUnderSource(target, targetOp, ancestor, it)) }
                    }
                }

                val sourceLike = sources.flatMap { (source, ops) -> ops.map { source to it } } +
                    derives.map { it.from to it }
                sourceLike.forEach { (source, sourceOp) ->
                    source.strictAncestors().forEach { ancestor ->
                        targets[ancestor].orEmpty().filter { it.createsTarget }.forEach { createOp ->
                            add(ReformErr.SourceUnderCreatedTarget(source, sourceOp, ancestor, createOp))
                        }
                    }
                }

                derives.forEach { derive ->
                    targets[derive.from]?.let { add(ReformErr.DeriveFromTarget(derive, it)) }
                    if (derive.from.type != derive.target.type) add(ReformErr.DeriveTypeMismatch(derive))
                }
            }

            return errors.toNonEmptyListOrNull()?.left()
                ?: ReformClaims(
                    sourceClaims = sources.mapValues { it.value.single() },
                    targetClaims = targets.mapValues { it.value.single() },
                    weakTargetClaims = weakTargets.mapValues { it.value.toSet() },
                    declarations = declarations,
                ).right()
        }

        private fun Ident.NonEmpty.strictAncestors(): Sequence<Ident.NonEmpty> = ancestorsAndSelf().drop(1)
    }
}

private fun Ident.NonEmpty.ancestorsAndSelf(): Sequence<Ident.NonEmpty> = sequence {
    var current: Ident = this@ancestorsAndSelf
    while (current is Ident.NonEmpty) {
        yield(current)
        current = current.dropLast()
    }
}
