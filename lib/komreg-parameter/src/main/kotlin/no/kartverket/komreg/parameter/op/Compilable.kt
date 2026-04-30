@file:OptIn(ExperimentalRaiseAccumulateApi::class)

package no.kartverket.komreg.parameter.op

import arrow.core.*
import arrow.core.raise.ExperimentalRaiseAccumulateApi
import arrow.core.raise.accumulate
import arrow.core.raise.either
import no.kartverket.komreg.parameter.data.Tuple
import no.kartverket.komreg.parameter.data.Tuple.Ap
import no.kartverket.komreg.parameter.data.append
import kotlin.collections.listOf

sealed interface Compilable<out A> {
    val cause: Cause<*> // TODO: Bind parameter

    sealed interface Cause<out A> : no.kartverket.komreg.parameter.op.Cause {
        class CodeLocation
            : no.kartverket.komreg.parameter.op.CodeLocation(computeStacktrace(2)), Cause<Nothing> {
            override val codeLocations: NonEmptyList<no.kartverket.komreg.parameter.op.CodeLocation>
                get() = nonEmptyListOf(this)
        }
        data class MergeCause<A : Tuple, B>(
            val merge: Merge<A, B>,
        ) : Cause<Ap<A, B>> {
            override val codeLocations: NonEmptyList<no.kartverket.komreg.parameter.op.CodeLocation>
                get() = merge.cause.codeLocations

            override fun toString(): String {
                return "Merge@${merge.cause.codeLocations.firstOrNull()}"
            }
        }
        class MergeOpCause<ParentInit: Tuple, ParentLast, A>(
            val mergeCause: MergeCause<ParentInit, ParentLast>,
            val mergeOp: Merge.MergeOp<out ParentInit, out ParentLast, out A>
        ) : Cause<A> {
            override val codeLocations: NonEmptyList<no.kartverket.komreg.parameter.op.CodeLocation>
                get() = nonEmptyListOf(mergeOp.codeLocation, *mergeCause.codeLocations.toTypedArray())

            override fun toString(): String {
                return "MergeOp@${mergeOp.codeLocation}"
            }
        }
    }
}
fun <Init : Tuple, Last> Merge<Init, Last>.getLoOps(): Pair<List<LoOp.MoveChildrenAndExpire<Ap<out Tuple, out Any?>>>, LoOp.TargetOp<Ap<Init, Last>>> {
    val merge = this@getLoOps
    val moveChildAndExpires = merge.from.map {
        LoOp.MoveChildrenAndExpire(merge.toOp.path, it, LoOp.Cause.SiblingOp(merge))
    }
    val createOrKeep: LoOp.TargetOp<Ap<Init, Last>> = when (merge.toOp) {
        is Merge.ToExisting<Init, Last> -> {
            LoOp.Keep(merge.toOp.path, merge.from, LoOp.Cause.MergeC(merge))
        }

        is Merge.ToNew<Init, Last> -> {
            LoOp.Create(merge.toOp.path, merge.from, merge.toOp.value, LoOp.Cause.MergeC(merge))
        }
    }
    return moveChildAndExpires to createOrKeep
}

fun <Init : Tuple, Last> Merge<Init, Last>.getTargetSupOpPairs(): Either<NonEmptyList<CompileError>, List<Pair<Ap<Init, Last>, SubOp<Ap<Init, Last>, *>>>> =
    either {
        val merge = this@getTargetSupOpPairs
        val mergeCause= Compilable.Cause.MergeCause(merge)
        accumulate {
            val convertedOps = merge.ops.mapNotNull { mergeOp ->
                if (!merge.from.contains(mergeOp.from.init)) {
                    accumulate { InputError(
                        "MergeOp ${mergeOp.from} is not in the set of the merge sources ${merge.from}",
                        Compilable.Cause.MergeOpCause(mergeCause, mergeOp)
                            .codeLocations
                    ) }
                    return@mapNotNull null
                }

                nullIfError(mergeOp.toSubOp(merge.toOp.path, mergeCause))
            }
            convertedOps
        }
    }

private fun <MergeToInit : Tuple, MergeToLast, A> Merge.MergeOp<MergeToInit, MergeToLast, A>.toSubOp(
    mergeTo: Ap<MergeToInit, MergeToLast>,
    mergeCause: Compilable.Cause.MergeCause<MergeToInit, MergeToLast>
): EitherNel<CompileError, Pair<Ap<MergeToInit, MergeToLast>, SubOp<Ap<MergeToInit, MergeToLast>, A>>> = either {
    val cause = Compilable.Cause.MergeOpCause(mergeCause, this@toSubOp)
    this@toSubOp.from.init to when (val op = this@toSubOp) {
        is Merge.Move<MergeToInit, MergeToLast, A> -> {
            SubOp.Move(op.from.last, mergeTo.append(op.to), op.`as`, cause).bind()
        }

        is Merge.Split<MergeToInit, MergeToLast, A> -> {
            accumulate {
                val subOps = op.ops.mapNotNull { subOp ->
                    nullIfError(subOp
                        .withUpdatedInit { toPath ->
                            mergeTo.append(toPath.init.last).append(toPath.last)
                        }
                        .map { it as SubOp.Invalidating }
                    )
                }
                SubOp.Split(op.from.last, subOps, cause).bind()
            }
        }
    }
}

fun <NewInit : Tuple, ParentInit : Tuple, Last> SubOp<ParentInit, Last>.withUpdatedInit(
    f: (Ap<ParentInit, Last>) -> Ap<NewInit, Last>
): EitherNel<CompileError, SubOp<NewInit, Last>> = either {
    when (val op = this@withUpdatedInit) {
        is SubOp.Adjust<ParentInit, Last> -> accumulate {
            SubOp.Adjust(
                op.from,
                op.ops.mapNotNull { subOp ->
                    nullIfError(subOp.withUpdatedInit { f(it.init).append(it.last) })
                },
                op.cause
            ).bind()
        }

        is SubOp.Create<Last> -> op

        is SubOp.Move<ParentInit, Last> -> SubOp.Move(
            op.from,
            f(op.to),
            `as`,
            op.cause
        ).bind()

        is SubOp.MoveRange<ParentInit, Last> -> {
            SubOp.MoveRange.invoke(
                op.from,
                op.fromEnd,
                f(op.to),
                op.partialNext,
                cause
            ).bind()
        }

        is SubOp.Split<ParentInit, Last> -> accumulate {
            SubOp.Split(
                op.from,
                op.ops.mapNotNull { subOp: SubOp.Invalidating<Ap<ParentInit, Last>, *> ->
                    nullIfError(subOp.withUpdatedInit { f(it.init).append(it.last) }) as? SubOp.Invalidating
                },
                op.cause
            ).bind()
        }
    }

}