package no.kartverket.komreg.regulering.reform.rules

import arrow.core.EitherNel
import arrow.core.left
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.regulering.reform.format.SegmentRegistry
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.ReformOp
import no.kartverket.komreg.transformation.reform.exclusiveTarget
import no.kartverket.komreg.transformation.reform.weakTarget

/**
 * Avviser operasjoner som er strukturelt gyldige, men som sinkene eller domenet ikke støtter. Ingen IO. Samler
 * alle feil. Reglene ligger som en tabell per identtype i [v1Capabilities].
 */
fun ReformClaims.validateDomain(registry: SegmentRegistry): EitherNel<DomainErr, Unit> {
    val errors = declarations.groupBy { registry.kindOf(it.key) }.flatMap { (kind, ops) ->
        val capabilities = kind?.let { v1Capabilities[it.name] }
            ?: return@flatMap ops.map { DomainErr.UnsupportedIdentType(it, it.key) }
        capabilities.violations(RuleScope(this, ops)) + ops.filterIsInstance<ReformOp.SourceOp>().mapNotNull {
            it.targetTypeMismatch(registry)
        }
    }
    return errors.distinct().toNonEmptyListOrNull()?.left() ?: Unit.right()
}

private fun SinkCapabilities.violations(scope: RuleScope): List<DomainErr> {
    val unsupported = scope.ops.mapNotNull { op ->
        val action = op.action
        when {
            action !in actions -> DomainErr.ActionNotSupported(op, label, action, hints[action] ?: defaultHint)
            op is ReformOp.Retire && retire == SuccessorPolicy.REQUIRED && op.successor == null ->
                DomainErr.RetireNeedsSuccessor(op, label)
            op is ReformOp.Retire && retire == SuccessorPolicy.FORBIDDEN && op.successor != null ->
                DomainErr.RetireHasSuccessor(op, label)
            else -> null
        }
    }
    val supported = scope.ops - unsupported.flatMap { it.ops }.toSet()
    return unsupported + checks.flatMap { it(RuleScope(scope.claims, supported)) }
}

private fun ReformOp.SourceOp.targetTypeMismatch(registry: SegmentRegistry): DomainErr? {
    val target = exclusiveTarget ?: weakTarget ?: return null
    return if (registry.kindOf(target) == registry.kindOf(source)) null else DomainErr.TargetTypeMismatch(this, target)
}

private val ReformOp.key: Ident.NonEmpty
    get() = when (this) {
        is ReformOp.SourceOp -> source
        is ReformOp.NewEntity -> target
    }

private val ReformOp.action: DomainAction
    get() = when (this) {
        is ReformOp.Create -> DomainAction.CREATE
        is ReformOp.Derive -> DomainAction.DERIVE
        is ReformOp.Move -> DomainAction.MOVE
        is ReformOp.RetireTo -> DomainAction.RETIRE_TO
        is ReformOp.Retire -> DomainAction.RETIRE
    }
