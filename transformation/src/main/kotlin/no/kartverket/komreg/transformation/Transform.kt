package no.kartverket.komreg.transformation

import arrow.core.*
import no.kartverket.komreg.transformation.error.AmbiguousTransform
import no.kartverket.komreg.transformation.rule.Rule

sealed interface Transform<in A> {

    operator fun <B : A> plus(other: Transform<B>): Either<AmbiguousTransform<B>, Transform<B>>
}

operator fun <A, B : A> Either<AmbiguousTransform<B>, Transform<A>>.plus(other: Transform<B>): Either<AmbiguousTransform<B>, Transform<B>> {
    return when(this) {
        is Either.Left -> {
            this.value.withMoreAmbiguity(other).left()
        }
        is Either.Right -> this.value + other
    }
}

data class ValueTransform<A>(val rules: NonEmptySet<Rule<A>>, val targetValue: A) :
    Transform<A> {
    constructor(rule: Rule<A>, targetValue: A) : this(nonEmptySetOf(rule), targetValue)

    override fun <X : A> plus(other: Transform<X>): Either<AmbiguousTransform<X>, Transform<X>> {
        return when (other) {
            is ValueTransform -> {
                if (targetValue == other.targetValue) {
                    ValueTransform(other.rules + rules, other.targetValue).right()
                } else {
                    AmbiguousTransform(this, nonEmptySetOf(other)).left()
                }
            }
            is NoTransform -> this.right()
        }
    }

}

sealed class NoTransform<in A> : Transform<A> {
    override fun <X : A> plus(other: Transform<X>): Either<AmbiguousTransform<A>, Transform<X>> {
        return other.right()
    }
}

internal object NoTransformNothing : NoTransform<Nothing>()
internal object NoTransformAny : NoTransform<Any>()