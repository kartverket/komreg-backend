package no.kartverket.komreg.transformation.error

import arrow.core.*
import com.google.common.collect.ImmutableRangeMap
import com.google.common.collect.ImmutableRangeSet
import com.google.common.collect.TreeRangeMap
import com.google.common.collect.TreeRangeSet
import no.kartverket.komreg.transformation.ComponentDomain
import no.kartverket.komreg.transformation.widen
import kotlin.reflect.KClass

sealed class RuleError {
    sealed class Uni<in Self : Uni<Self>> : RuleError() {
        abstract operator fun <Other : Self> plus(other: Other): Uni<Self>
    }

    class Multi(val errors: Map<KClass<out Uni<*>>, Uni<*>>) : RuleError() {
        fun plus(other: Multi): Multi {
            val errors = errors
                .toMutableMap()
                .apply {
                    for ((key, value) in other.errors) {
                        compute(key) { _, existingValue ->
                            if (existingValue == null) {
                                value
                            } else {
                                existingValue + value
                            }
                        }
                    }
                }
            return Multi(errors)
        }

        fun plus(other: Uni<*>): Multi {
            val errors = errors
                .toMutableMap()
                .apply {
                    compute(other::class) { _, existing ->
                        if (existing == null) {
                            other
                        } else {
                            existing + other
                        }
                    }
                }
            return Multi(errors)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Multi
            return errors == other.errors
        }

        override fun hashCode(): Int {
            return errors.hashCode()
        }

    }

    operator fun plus(other: RuleError): RuleError {
        return when (other) {
            is Multi -> when (this) {
                is Uni<*> -> other.plus(this)
                is Multi -> other.plus(this)
            }

            is Uni<*> -> when (this) {
                is Uni<*> -> this.plus(other)
                is Multi -> this.plus(other)
            }
        }
    }
}

sealed class RangedRuleError<in Self : RangedRuleError<Self, A, B>, A : Comparable<A>, B : Comparable<B>> :
    RuleError.Uni<Self>() {
    abstract val rules: NonEmptySet<CanCauseRangeError<A, B>>
}

data class DomainMismatch<A : Comparable<A>>(
    val domain: ComponentDomain<A>,
    val mismatches: NonEmptySet<ComponentDomain<*>>
) : RuleError.Uni<DomainMismatch<A>>() {

    override fun <Other : DomainMismatch<A>> plus(other: Other): DomainMismatch<A> {
        return DomainMismatch(domain, mismatches + other.mismatches)
    }
}

sealed class EmptyMultiRule<in Self : EmptyMultiRule<Self, A>, A : Comparable<A>> : RuleError.Uni<Self>() {
    abstract val domain: ComponentDomain<in A>
}

data class NoRules<A : Comparable<A>>(
    override val domain: ComponentDomain<in A>,
    val rules: NonEmptySet<CanCauseRangeError<A, A>>
) : EmptyMultiRule<NoRules<A>, A>() {

    override fun <Other : NoRules<A>> plus(other: Other): Uni<NoRules<A>> {
        return NoRules(domain, rules + other.rules)
    }
}

data class NoSplitEntries<A : Comparable<A>>(
    override val domain: ComponentDomain<in A>,
) : EmptyMultiRule<NoSplitEntries<A>, A>() {

    override fun <Other : NoSplitEntries<A>> plus(other: Other): Uni<NoSplitEntries<A>> {
        return NoSplitEntries(domain)
    }
}

class ConflictingTargetValue<A : Comparable<A>, B : Comparable<B>> private constructor(
    val conflictRanges: ImmutableRangeMap<A, ImmutableRangeSet<B>>,
    override val rules: NonEmptySet<CanCauseRangeError<A, B>>
) : RangedRuleError<ConflictingTargetValue<A, B>, A, B>() {
    companion object {
        operator fun <A : Comparable<A>, B : Comparable<B>> invoke(
            allRules: NonEmptySet<CanCauseRangeError<A, B>>
        ): ConflictingTargetValue<A, B> {
            require(allRules.size > 1) { "Must have at least two rules: $allRules" }

            val conflictRanges =
                allRules
                    .asIterable()
                    .flatMap { rule -> rule.targetRanges.asRanges().map { targetRange -> targetRange.widen() to rule } }
                    .groupingBy { it.first }
                    .fold(TreeRangeSet.create<A>()) { rangeSet, (_, rule) ->
                        rangeSet.apply {
                            for (sourceRange in rule.sourceRanges.widen().asRanges()) {
                                add(sourceRange)
                            }
                        }
                    }
                    .entries
                    .flatMap { (targetRange, sourceRanges) ->
                        sourceRanges
                            .asRanges()
                            .map { sourceRange -> sourceRange to targetRange }
                    }
                    .fold(TreeRangeMap.create<A, ImmutableRangeSet<B>>()) { rangeMap, (sourceRange, targetRange) ->
                        rangeMap.apply {
                            merge(sourceRange, ImmutableRangeSet.of(targetRange), ImmutableRangeSet<B>::union)
                        }
                        rangeMap
                    }
                    .let { ImmutableRangeMap.copyOf(it) }



            require(conflictRanges.asMapOfRanges().isNotEmpty()) {
                "Must have at least one conflicting target range: $conflictRanges"
            }

            return ConflictingTargetValue(conflictRanges, allRules)
        }
    }

    override fun <Other : ConflictingTargetValue<A, B>> plus(other: Other): Uni<ConflictingTargetValue<A, B>> {
        val conflictRanges = conflictRanges.asMapOfRanges().entries.plus(other.conflictRanges.asMapOfRanges().entries)
            .fold(TreeRangeMap.create<A, ImmutableRangeSet<B>>()) { rangeMap, (sourceRange, targetRange) ->
                rangeMap.apply {
                    merge(sourceRange, targetRange, ImmutableRangeSet<B>::union)
                }
            }
            .asMapOfRanges()
            .fold(ImmutableRangeMap.builder<A, ImmutableRangeSet<B>>()) { builder, (sourceRange, targetRange) ->
                builder.put(sourceRange, targetRange)
            }
            .build()
        return ConflictingTargetValue(conflictRanges, rules + other.rules)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConflictingTargetValue<*, *>

        if (conflictRanges != other.conflictRanges) return false
        if (rules != other.rules) return false

        return true
    }

    override fun hashCode(): Int {
        var result = conflictRanges.hashCode()
        result = 31 * result + rules.hashCode()
        return result
    }

    override fun toString(): String {
        return "ConflictingTargetValue(conflictRanges=$conflictRanges, rules=$rules)"
    }

}

