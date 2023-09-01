package no.kartverket.komreg.transformation.rule

import arrow.core.*
import arrow.core.raise.either
import com.google.common.collect.ImmutableRangeSet
import com.google.common.collect.Range
import no.kartverket.komreg.transformation.ComponentDomain
import no.kartverket.komreg.transformation.Transform
import no.kartverket.komreg.transformation.error.RuleError
import no.kartverket.komreg.transformation.error.TransformError

class Copy<A : Comparable<A>>(
    val backingRule: ComponentRule.NonCopy<A>,
    override val explicitRules: NonEmptySet<ComponentRule.Explicit<*>>
) : ComponentRule.Implicit<A> {


    override val domain: ComponentDomain<A>
        get() = backingRule.domain

    override val sourceRanges: ImmutableRangeSet<A> get() = backingRule.sourceRanges

    override val targetRanges: ImmutableRangeSet<A> get() = backingRule.targetRanges

    override fun plus(other: ComponentRule<out A>): Either<RuleError, ComponentRule<A>> = either {
        when (val sum = (backingRule + other).bind()) {
            is Copy -> Copy(sum.backingRule, sum.explicitRules + explicitRules)
            is ComponentRule.NonCopy -> {
                val explicitRules = explicitRules + when (other) {
                    is Copy -> other.explicitRules
                    is ComponentRule.Explicit -> nonEmptySetOf(other)
                    is ComponentRule.Implicit -> other.explicitRules
                }
                Copy(sum, explicitRules)
            }
        }
    }

    override fun <X : A> asCopy(otherDomain: ComponentDomain<X>): Copy<X>? {
        if (backingRule.domain == otherDomain) {
            @Suppress("UNCHECKED_CAST")
            return this as Copy<X>
        } else {
            val backingRule = backingRule.toDomain(otherDomain)?.toNonCopy() ?: return null
            return Copy(backingRule, explicitRules)
        }
    }

    fun plus(other: Copy<out A>): Either<RuleError, Copy<A>> = either {
        when (val sum = (backingRule + other.backingRule).bind()) {
            is Copy -> Copy(sum.backingRule, sum.explicitRules + explicitRules + other.explicitRules)
            is ComponentRule.NonCopy -> Copy(sum, explicitRules + other.explicitRules)
        }
    }

    fun plus(other: ComponentRule.Explicit<out A>): Either<RuleError, Copy<A>> = either {
        when (val newBackingRule = (backingRule + other).bind()) {
            is Copy -> {
                Copy(newBackingRule.backingRule, explicitRules + newBackingRule.explicitRules + other)
            }
            is ComponentRule.NonCopy -> if (newBackingRule != backingRule) {
                Copy(newBackingRule, explicitRules + other)
            } else {
                this@Copy
            }
        }
    }


    override fun toNonCopy(): ComponentRule.NonCopy<A> {
        return backingRule
    }

    override fun narrowSourceRange(newSourceRange: Range<out A>): ComponentRule<A>? {
        val backingRule= backingRule.narrowSourceRange(newSourceRange) ?: return null
        return when (backingRule) {
            is ComponentRule.NonCopy -> Copy(backingRule, explicitRules)
            is Copy -> backingRule
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Copy<*>

        if (backingRule != other.backingRule) return false
        if (explicitRules != other.explicitRules) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backingRule.hashCode()
        result = 31 * result + explicitRules.hashCode()
        return result
    }

    override fun toString(): String {
        return "Copy(backingRule=$backingRule, explicitRules=$explicitRules)"
    }

    override fun transform(values: Iterable<Comparable<*>>): Either<TransformError, List<Transform<*>>> {
        return backingRule.transform(values)
    }

}

