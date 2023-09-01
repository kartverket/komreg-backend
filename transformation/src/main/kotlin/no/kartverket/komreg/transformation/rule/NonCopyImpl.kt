package no.kartverket.komreg.transformation.rule

import arrow.core.Either
import arrow.core.mapOrAccumulate
import arrow.core.nonEmptySetOf
import arrow.core.raise.either
import com.google.common.base.Objects
import no.kartverket.komreg.transformation.ComponentDomain
import no.kartverket.komreg.transformation.NoTransformNothing
import no.kartverket.komreg.transformation.Transform
import no.kartverket.komreg.transformation.error.DomainMismatch
import no.kartverket.komreg.transformation.error.RuleError
import no.kartverket.komreg.transformation.error.TransformError
import kotlin.reflect.safeCast

sealed class NonCopyImpl<A : Comparable<A>>(override val domain: ComponentDomain<A>) : ComponentRule.NonCopy<A> {
    operator fun plus(other: Copy<out A>): Either<RuleError, Copy<A>> = either {
        val explicitRules = when (this@NonCopyImpl) {
            is ComponentRule.Explicit -> nonEmptySetOf(this@NonCopyImpl)
            is Merge -> this@NonCopyImpl.explicitRules
            is Switch -> this@NonCopyImpl.explicitRules
        }
        when (val backingRule = plus(other.backingRule).bind()) {
            is ComponentRule.NonCopy -> Copy(backingRule, explicitRules + other.explicitRules)
            is Copy -> Copy(
                backingRule.backingRule,
                explicitRules + other.explicitRules + backingRule.explicitRules
            )
        }
    }

    override fun transform(values: Iterable<Comparable<*>>): Either<TransformError, List<Transform<*>>> = either {
        values
            .mapOrAccumulate(TransformError::plus) { value ->
                val typedValue = domain.classifier.safeCast(value)?.takeIf { sourceRanges.contains(it) }
                if (typedValue == null) {
                    NoTransformNothing
                } else {
                    transformComponentUnconditionally(typedValue).bind()
                }
            }
            .bind()
    }

    internal abstract fun transformComponentUnconditionally(value: A): Either<TransformError.Uni, Transform<A>>

    final override fun toNonCopy(): ComponentRule.NonCopy<A> = this

    final override fun <X : A> toDomain(otherDomain: ComponentDomain<X>): ComponentRule<X>? {
        return super.toDomain(otherDomain)
    }

    final override fun <X : A> toDomainOrLeft(otherDomain: ComponentDomain<X>): Either<DomainMismatch<A>, ComponentRule<X>> {
        return super.toDomainOrLeft(otherDomain)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (!canEqual(other)) return false
        other as ComponentRule.NonCopy<*>
        return domain == other.domain && sourceRanges == other.sourceRanges && targetRanges == other.targetRanges
    }

    protected abstract fun canEqual(other: Any): Boolean

    override fun hashCode(): Int {
        return Objects.hashCode(domain, sourceRanges, targetRanges)
    }

    final override fun toString(): String {
        return "${this::class.simpleName}(domain=${domain}, sourceRanges=${sourceRanges}, targetRanges=${targetRanges})"
    }

}