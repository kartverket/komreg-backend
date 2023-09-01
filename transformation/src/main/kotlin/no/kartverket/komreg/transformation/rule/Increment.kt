package no.kartverket.komreg.transformation.rule

import arrow.core.Either
import arrow.core.nonEmptySetOf
import arrow.core.raise.either
import arrow.core.right
import com.google.common.collect.ImmutableRangeSet
import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import no.kartverket.komreg.transformation.*
import no.kartverket.komreg.transformation.NoTransformAny
import no.kartverket.komreg.transformation.error.ConflictingTargetValue
import no.kartverket.komreg.transformation.error.RuleError
import no.kartverket.komreg.transformation.error.TransformError

class Increment<A : Comparable<A>>(
    domain: ComponentDomain<A>,
    sourceRanges: RangeSet<out A>,
    val offset: Long
) : ComponentRule.Explicit<A>(domain, sourceRanges, offsetRanges(domain, sourceRanges, offset)), ComponentRule.NonSplit<A> {
    companion object {
        private fun <A : Comparable<A>> offsetRanges(
            domain: ComponentDomain<A>,
            ranges: RangeSet<out A>,
            offset: Long
        ): ImmutableRangeSet<A> {
            return ranges
                .asRanges()
                .map { range -> offsetRange(domain, range, offset) }
                .let { ImmutableRangeSet.unionOf(it) }
        }

        private fun <A : Comparable<A>> offsetRange(
            domain: ComponentDomain<A>,
            range: Range<out A>,
            offset: Long
        ): Range<A> {
            with(domain) {
                val lowerEndpoint = if (range.hasLowerBound()) {
                    (range.lowerEndpoint() + offset) ?: throw IllegalArgumentException(
                        "Cannot offset $range by $offset: Lower endpoint is less than ${domain.minValue}"
                    )
                } else {
                    null
                }
                val upperEndpoint = if (range.hasUpperBound()) {
                    (range.upperEndpoint() + offset) ?: throw IllegalArgumentException(
                        "Cannot offset $range by $offset: Upper endpoint is greater than ${domain.maxValue}"
                    )
                } else {
                    null
                }
                val offsetRange = if (lowerEndpoint != null) {
                    if (upperEndpoint != null) {
                        Range.range(lowerEndpoint, range.lowerBoundType(), upperEndpoint, range.upperBoundType())
                    } else {
                        Range.downTo(lowerEndpoint, range.lowerBoundType())
                    }
                } else if (upperEndpoint != null) {
                    Range.upTo(upperEndpoint, range.upperBoundType())
                } else {
                    Range.all()
                }
                return canonicalRange(offsetRange)
            }
        }
    }

    init {
        require(domain.span(sourceRanges.widen().span())?.let { it > 1L } ?: true) { "Increment must span at least two source values" }
    }

    override fun plus(other: ComponentRule<out A>): Either<RuleError, ComponentRule<A>> = either {
        val sourceRangeIntersect = sourceRanges.intersection(other.sourceRanges.widen())
        if (!sourceRangeIntersect.isEmpty) {
            Switch(domain, nonEmptySetOf(this@Increment, other))
        }
        when (other) {
            is Copy -> plus(other).bind()
            is Switch -> other.withRuleAdded(domain, this@Increment).bind()
            is SplitOrAdjust, is Merge -> Switch(domain, nonEmptySetOf(this@Increment, other)).bind()
            is Increment -> {
                if (offset == other.offset) {
                    if (sourceRanges == other.sourceRanges) {
                        this@Increment
                    } else {
                        Copy(
                            Increment(domain, sourceRanges.union(other.sourceRanges.widen()), offset),
                            nonEmptySetOf(this@Increment, other)
                        )
                    }
                } else {
                    raise(ConflictingTargetValue(nonEmptySetOf(this@Increment, other)))
                }
            }
            is Specific -> {
                with(domain) {
                    if (other.targetValue - offset == other.sourceValue) {
                        this@Increment
                    } else {
                        raise(ConflictingTargetValue(nonEmptySetOf(this@Increment, other)))
                    }
                }
            }
        }
    }

    override fun <X : A> asCopy(otherDomain: ComponentDomain<X>): Copy<X>? {
        return Copy(Increment(otherDomain, sourceRanges.narrowToDomainOrNull(otherDomain) ?: return null, offset), nonEmptySetOf(this))
    }

    override fun transformComponentUnconditionally(value: A): Either<TransformError.Uni, Transform<A>> {
        return with(domain) {
            val targetValue = (value + offset) ?: return NoTransformAny.right()
            ValueTransform(this@Increment, targetValue)
        }.right()

    }

    override fun canEqual(other: Any): Boolean {
        return other is Increment<*>
    }
}