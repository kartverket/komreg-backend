package no.kartverket.komreg.transformation.rule

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import com.google.common.collect.ImmutableRangeSet
import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import no.kartverket.komreg.transformation.ComponentDomain
import no.kartverket.komreg.transformation.error.DomainMismatch
import no.kartverket.komreg.transformation.error.CanCauseRangeErrorImpl
import no.kartverket.komreg.transformation.error.RuleError
import no.kartverket.komreg.transformation.widenToDomainOrNull

/**
 *
 *
 * |               | Increment | Merge | Specific       | Split |
 * |--------------:|:---------:|:-----:|:--------------:|:-----:|
 * | **Increment** | Increment |       |                |       |
 * | **Merge**     |           | Merge |                |       |
 * | **Specific**  | Increment | Merge | Specific/Merge |       |
 * | **Split**     |           |       |                | Split |
 * | **Switch**    |           |       |                |       |
 *
 */
sealed interface ComponentRule<A : Comparable<A>> : ComponentRuleLike<A>, CanCauseRangeErrorImpl<A> {

    override val domain: ComponentDomain<A>

    sealed interface NonCopy<A : Comparable<A>> : ComponentRule<A>
    sealed interface NonSplit<A : Comparable<A>> : NonCopy<A>
    sealed interface NonSwitch<A : Comparable<A>> : NonCopy<A>
    sealed interface SingleTarget<A : Comparable<A>> : NonSplit<A>, NonSwitch<A> {
        val targetValue: A
    }

    sealed class Explicit<A : Comparable<A>>(
        domain: ComponentDomain<A>,
        sourceRanges: RangeSet<out A>,
        targetRanges: RangeSet<out A>
    ) : NonCopyImpl<A>(domain), NonSwitch<A> {
        sealed class SingleSource<A : Comparable<A>>(
            domain: ComponentDomain<A>,
            val sourceValue: A,
            targetRanges: RangeSet<out A>
        ) : Explicit<A>(
            domain,
            ImmutableRangeSet.of(Range.singleton(sourceValue)),
            targetRanges
        )

        final override val sourceRanges: ImmutableRangeSet<A> = requireNotNull(sourceRanges.widenToDomainOrNull(domain)) {
            "Source ranges $sourceRanges does not match domain $domain"
        }

        final override val targetRanges: ImmutableRangeSet<A> = requireNotNull(targetRanges.widenToDomainOrNull(domain)) {
            "Target ranges $targetRanges does not match domain $domain"
        }

    }


    sealed interface Implicit<A : Comparable<A>> : ComponentRule<A> {
        val explicitRules: NonEmptySet<Explicit<*>>
    }

    override val sourceRanges: ImmutableRangeSet<A>
    override val targetRanges: ImmutableRangeSet<A>

    operator fun plus(other: ComponentRule<out A>): Either<RuleError, ComponentRule<A>>

    fun <X : A> asCopy(otherDomain: ComponentDomain<X>): Copy<X>?

    fun <X : A> toDomain(otherDomain: ComponentDomain<X>): ComponentRule<X>? {
        if (domain == otherDomain) {
            @Suppress("UNCHECKED_CAST")
            return this as ComponentRule<X>
        } else {
            return asCopy(otherDomain)
        }
    }

    fun <X : A> toDomainOrLeft(otherDomain: ComponentDomain<X>): Either<DomainMismatch<A>, ComponentRule<X>> =
        toDomain(otherDomain)?.right() ?: DomainMismatch(domain, nonEmptySetOf(otherDomain)).left()

    fun toNonCopy(): NonCopy<A>

    fun allDomains(): NonEmptySet<ComponentDomain<*>> = nonEmptySetOf(domain)
}

sealed interface ComponentRuleLike<in A : Comparable<*>> : Rule<A>

operator fun <A : Comparable<A>, B : A> ComponentRuleLike<A>.plus(other: ComponentRuleLike<B>): Either<RuleError, ComponentRule<A>> {
    val a = when (this) {
        is ComponentRule -> this
    }
    val b = when (other) {
        is ComponentRule -> other
    }
    return a + b
}

operator fun <A : Comparable<A>, B : A> ComponentRuleLike<A>.plus(other: Either<RuleError, ComponentRuleLike<B>>): Either<RuleError, ComponentRule<A>> {
    val a = when (this) {
        is ComponentRule -> this
    }
    val b = when (other) {
        is Either.Left -> return other
        is Either.Right -> when (val b = other.value) {
            is ComponentRule -> b
        }
    }
    return a + b
}

operator fun <A : Comparable<A>, B : A> Either<RuleError, ComponentRuleLike<A>>.plus(
    other: ComponentRuleLike<B>
): Either<RuleError, ComponentRule<A>> {
    val a = when (this) {
        is Either.Left -> return this
        is Either.Right -> when (this.value) {
            is ComponentRule -> this.value
        }
    }
    val b = when (other) {
        is ComponentRule -> other
    }
    return a + b
}

operator fun <A : Comparable<A>, B : A> Either<RuleError, ComponentRuleLike<A>>.plus(
    other: Either<RuleError,ComponentRuleLike<B>>
): Either<RuleError, ComponentRule<A>> = either {
    zipOrAccumulate(
        RuleError::plus,
        { when (val a = this@plus.bind()) { is ComponentRule -> a} },
        { when (val b = other.bind()) { is ComponentRule -> b} }) { a, b ->
        a + b
    }.bind()
}




