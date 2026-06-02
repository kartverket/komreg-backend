@file:OptIn(ExperimentalRaiseAccumulateApi::class)

package no.kartverket.komreg.parameter.op

import arrow.core.EitherNel
import arrow.core.NonEmptySet
import arrow.core.nel
import arrow.core.prependTo
import arrow.core.raise.ExperimentalRaiseAccumulateApi
import arrow.core.raise.accumulate
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.toNonEmptyListOrThrow
import arrow.core.toNonEmptySetOrThrow
import no.kartverket.komreg.core.domain.TypedPayload
import no.kartverket.komreg.parameter.data.Tuple
import no.kartverket.komreg.parameter.data.Tuple.Ap

data class Merge<Init : Tuple, Last> private constructor(
    val from: NonEmptySet<Ap<Init, Last>>,
    val toOp: Target<Init, Last>,
    val ops: List<MergeOp<Init, Last, *>>,
    override val cause: Compilable.Cause<Last>
) : Compilable<Ap<Tuple.Empty, Any>> {

    sealed interface MergeOp<ParentInit : Tuple, ParentLast, A> {
        val from: Ap<Ap<ParentInit, ParentLast>, A>
        val codeLocation: Compilable.Cause.CodeLocation
    }

    sealed interface Target<Init : Tuple, Last> {
        val path: Ap<Init, Last>
    }
    @JvmInline
    value class ToExisting<Init : Tuple, Last>(override val path: Ap<Init, Last>) : Target<Init, Last>
    data class ToNew<Init : Tuple, Last>(override val path: Ap<Init, Last>, val value: TypedPayload<Last>) : Target<Init, Last>

    data class Move<ParentInit: Tuple, ParentLast, A>(
        override val from: Ap<Ap<ParentInit, ParentLast>, A>,
        val to: A,
        val `as`: SubOp.Move.As<A> = SubOp.Move.As.Same,
        override val codeLocation: Compilable.Cause.CodeLocation = Compilable.Cause.CodeLocation()
    ) : MergeOp<ParentInit, ParentLast, A>

    data class Split<ParentInit: Tuple, ParentLast, A>(
        override val from: Ap<Ap<ParentInit, ParentLast>, A>,
        val ops: List<SubOp.Invalidating<Ap<Tuple.Empty, A>, *>>,
        override val codeLocation: Compilable.Cause.CodeLocation = Compilable.Cause.CodeLocation()
    ) : MergeOp<ParentInit, ParentLast, A>

    companion object {
        operator fun <Init : Tuple, Last> invoke(
            from: Collection<Ap<Init, Last>>,
            toOp: Target<Init, Last>,
            ops: List<MergeOp<Init, Last, *>>,
            cause: Compilable.Cause<Last> = Compilable.Cause.CodeLocation()
        ): EitherNel<InputError, Merge<Init, Last>> = either {
            val from = from.toHashSet()
            ensure(from.size > 1) {
                InputError("Merge: from must contain at least two distinct entries", cause.codeLocations).nel()
            }
            ensure(!from.contains(toOp.path)) {
                InputError("Merge: ${toOp.path} is both source and target", cause.codeLocations).nel()
            }
            accumulate {
                val lasts = from.mapTo(HashSet()) { it.last }
                for (op in ops) {
                    if (!lasts.contains(op.from.init.last)) {
                        accumulate(
                            InputError(
                                "Merge: op.from ${op.from} does not match any of the from entries",
                                op.codeLocation.prependTo(cause.codeLocations).toNonEmptyListOrThrow()
                            )
                        )
                    }
                }
            }
            Merge(from.toNonEmptySetOrThrow(), toOp, ops, cause)
        }
    }
}