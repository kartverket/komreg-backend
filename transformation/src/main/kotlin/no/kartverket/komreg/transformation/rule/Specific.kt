package no.kartverket.komreg.transformation.rule

import arrow.core.Either
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

class Specific<A : Comparable<A>>(
    domain: ComponentDomain<A>,
    sourceValue: A,
    override val targetValue: A
) : ComponentRule.Explicit.SingleSource<A>(
    domain,
    sourceValue,
    ImmutableRangeSet.of(Range.singleton(targetValue))
), ComponentRule.SingleTarget<A>, ComponentRule.NonSplit<A> {
    override fun plus(other: ComponentRule<out A>): Either<RuleError, ComponentRule<A>> = either {
        when (other) {
            is Copy -> plus(other).bind()
            is Switch -> other.withRuleAdded(domain, this@Specific).bind()
            is SplitOrAdjust -> Switch(domain, nonEmptySetOf(this@Specific, other)).bind()
            is Merge -> {
                if (other.sourceRanges.widen().contains(sourceValue)) {
                    if (other.targetValue == targetValue) {
                        if (other.explicitRules.contains(this@Specific)) {
                            other.toDomainOrLeft(domain).bind()
                        } else {
                            Merge(
                                domain,
                                other.sourceRanges.widenToDomainOrLeft(domain).bind().union(sourceRanges),
                                targetValue,
                                other.explicitRules + this@Specific
                            )
                        }
                    } else {
                        raise(ConflictingTargetValue(nonEmptySetOf(this@Specific, other)))
                    }
                } else {
                    Switch(domain, nonEmptySetOf(this@Specific, other)).bind()
                }
            }
            is Increment -> with(domain) {
                if (targetValue - other.offset == sourceValue) {
                    other.toDomainOrLeft(domain).bind()
                } else {
                    raise(raise(ConflictingTargetValue(nonEmptySetOf(this@Specific, other))))
                }
            }
            is Specific ->
                if (other.sourceValue != sourceValue && other.targetValue != targetValue) {
                    Switch(domain, nonEmptySetOf(this@Specific, other)).bind()
                } else if (other.targetValue == targetValue) {
                    if (other.sourceValue == sourceValue) {
                        this@Specific
                    } else {
                        Merge(domain, sourceRanges.union(other.sourceRanges.widenToDomainOrLeft(domain).bind()), targetValue, nonEmptySetOf(this@Specific, other))
                    }
                } else {
                    raise(ConflictingTargetValue(nonEmptySetOf(this@Specific, other)))
                }
        }
    }

    override fun <X : A> asCopy(otherDomain: ComponentDomain<X>): Copy<X>? {
        val sourceValue = otherDomain.classifier.safeCast(sourceValue) ?: return null
        val targetValue = otherDomain.classifier.safeCast(targetValue) ?: return null
        return Copy(Specific(otherDomain, sourceValue, targetValue), nonEmptySetOf(this))
    }

    override fun canEqual(other: Any): Boolean {
        return other is Specific<*>
    }

    override fun transformComponentUnconditionally(value: A): Either<TransformError.Uni, Transform<A>> {
        return ValueTransform(this, targetValue).right()
    }
}