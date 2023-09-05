package no.kartverket.komreg.transformation.rule

import arrow.core.Either
import arrow.core.NonEmptySet
import arrow.core.nonEmptySetOf
import arrow.core.raise.either
import arrow.core.right
import com.google.common.collect.ImmutableRangeSet
import com.google.common.collect.Range
import no.kartverket.komreg.transformation.*
import no.kartverket.komreg.transformation.error.ConflictingTargetValue
import no.kartverket.komreg.transformation.error.RuleError
import no.kartverket.komreg.transformation.error.TransformError
import kotlin.reflect.safeCast

class Merge<A : Comparable<A>> internal constructor(
    domain: ComponentDomain<A>,
    override val sourceRanges: ImmutableRangeSet<A>,
    override val targetValue: A,
    override val explicitRules: NonEmptySet<ComponentRule.Explicit<*>>
) : NonCopyImpl<A>(domain), ComponentRule.Implicit<A>, ComponentRule.NonSwitch<A>, ComponentRule.NonSplit<A>,
    ComponentRule.SingleTarget<A> {
    override val targetRanges: ImmutableRangeSet<A> =
        ImmutableRangeSet.of(domain.canonicalRange(Range.singleton(targetValue)))

    override fun narrowSourceRange(newSourceRange: Range<out A>): ComponentRule<A>? {
        if (newSourceRange.widen().encloses(sourceRanges.span())) {
            return this
        } else {
            val newSourceRanges = ImmutableRangeSet
                .of(newSourceRange.widen())
                .narrowToDomainOrNull(domain)
                ?.takeIf { rangeSet -> !rangeSet.isEmpty }
                ?: return null
            return Copy(Merge(domain, newSourceRanges, targetValue, explicitRules), explicitRules)
        }
    }

    override fun <X : A> asCopy(otherDomain: ComponentDomain<X>): Copy<X>? {
        val sourceRanges = sourceRanges.narrowToDomainOrNull(otherDomain) ?: return null
        val targetValue = otherDomain.classifier.safeCast(targetValue) ?: return null
        if (targetValue !in otherDomain) return null
        return Copy(Merge(otherDomain, sourceRanges, targetValue, explicitRules), explicitRules)
    }

    override fun plus(other: ComponentRule<out A>): Either<RuleError, ComponentRule<A>> = either {
        when (other) {
            is Copy -> plus(other).bind()
            is Switch -> other.withRuleAdded(domain, this@Merge).bind()
            is SplitOrAdjust, is Increment -> Switch(domain, nonEmptySetOf(this@Merge, other)).bind()
            is Merge ->
                if (targetValue == other.targetValue) {
                    val sourceRanges = sourceRanges.union(other.sourceRanges.widenToDomainOrLeft(domain).bind())
                    Merge(domain, sourceRanges, targetValue, explicitRules + other.explicitRules)
                } else {
                    raise(ConflictingTargetValue(nonEmptySetOf(this@Merge, other)))
                }
            is Specific -> {
                if (other.targetValue == targetValue) {
                    val sourceRanges = sourceRanges.union(other.sourceRanges.widenToDomainOrLeft(domain).bind())
                    Merge(domain, sourceRanges, targetValue, explicitRules + other)
                } else if (sourceRanges.contains(other.sourceValue)) {
                    raise(
                        ConflictingTargetValue(nonEmptySetOf(this@Merge, other))
                    )
                } else {
                    Switch(domain, nonEmptySetOf(this@Merge, other)).bind()
                }
            }
        }
    }

    override fun transformComponentUnconditionally(value: A): Either<TransformError.Uni, Transform<A>> {
        return ValueTransform(this, targetValue).right()
    }

    override fun canEqual(other: Any): Boolean {
        return other is Merge<*>
    }


}