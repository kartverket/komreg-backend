package no.kartverket.komreg.transformation.error

import arrow.core.*
import no.kartverket.komreg.transformation.rule.Split
import no.kartverket.komreg.transformation.Transform

sealed class TransformError {
         operator fun plus(other: TransformError): TransformError {
             val otherErrors = when(other) {
                    is Multi -> other.errors
                    is Uni -> nonEmptySetOf(other)
             }
             val thisErrors = when (this) {
                 is Multi -> this.errors
                 is Uni -> nonEmptySetOf(this)
             }

             val allErrors = (thisErrors + otherErrors)
                 .groupingBy { it::class }
                 .reduce { _, a, b ->
                     when(a) {
                         is AmbiguousTransform<*> -> a + (b as AmbiguousTransform<*>)
                         is MissingComponentValue<*> -> a + (b as MissingComponentValue<*>)
                         is ConflictingSplitTarget<*> -> a + (b as ConflictingSplitTarget<*>)
                     }
                 }
                 .values
                 .toNonEmptySetOrNull()!!

             return if (allErrors.size == 1) {
                 allErrors.head
             } else {
                 Multi(allErrors.toNonEmptySetOrNull()!!)
             }
         }

    data class Multi(val errors: NonEmptySet<Uni>) : TransformError()
    sealed class Uni : TransformError()
}



class AmbiguousTransform<in A> private constructor(
    val transforms: NonEmptySet<Transform<A>>
) : TransformError.Uni() {

    constructor(
        firstTransform: Transform<A>,
        moreTransforms: NonEmptySet<Transform<A>>
    ) : this(moreTransforms + firstTransform)

    operator fun <B : A> plus(other: AmbiguousTransform<B>): AmbiguousTransform<B> {
        return AmbiguousTransform(other.transforms + transforms)
    }

    internal fun <B : A> withMoreAmbiguity(ambiguity: Transform<B>): AmbiguousTransform<B> {
        return AmbiguousTransform((transforms as NonEmptySet<Transform<B>>).plus(ambiguity))
    }
}

data class MissingComponentValue<A : Comparable<A>>(val splitRule: NonEmptySet<Split<in A>>) : TransformError.Uni() {

    operator fun <B : A> plus(other: MissingComponentValue<in B>): MissingComponentValue<B> {
        return MissingComponentValue(other.splitRule + splitRule)
    }
}

data class ConflictingSplitTarget<A : Comparable<A>>(
    val targetValue: A,
    val targetValues: NonEmptySet<*>
) : TransformError.Uni() {
    operator fun <B : A> plus(other: ConflictingSplitTarget<in B>): ConflictingSplitTarget<A> {
        return ConflictingSplitTarget(targetValue, (other.targetValues + targetValues).toNonEmptySetOrNull()!!)
    }
}

