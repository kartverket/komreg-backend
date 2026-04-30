package no.kartverket.komreg.parameter.op

import arrow.core.EitherNel
import arrow.core.nel
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import no.kartverket.komreg.core.data.PartialNext
import no.kartverket.komreg.core.domain.TypedPayload
import no.kartverket.komreg.parameter.data.Tuple
import no.kartverket.komreg.parameter.data.Tuple.Ap
import no.kartverket.komreg.parameter.data.append
import no.kartverket.komreg.parameter.data.narrow

sealed interface SubOp<out Init : Tuple, Last> : Compilable<Ap<out Init, Last>> {
    override val cause: Compilable.Cause<Last>

    sealed interface Invalidating<out Init : Tuple, Last> : SubOp<Init, Last>
    sealed interface AdjustOrSplit<out Init : Tuple, Last> : SubOp<Init, Last> {
        val from: Last
        val ops: List<SubOp<Ap<out Init, Last>, *>>
    }

    data class Adjust<Init : Tuple, Last> private constructor(
        override val from: Last,
        override val ops: List<SubOp<Ap<Init, Last>, *>>,
        override val cause: Compilable.Cause<Last>
    ) : AdjustOrSplit<Init, Last> {
        companion object {
            operator fun <Init : Tuple, Last> invoke(
                from: Last,
                ops: List<SubOp<Ap<Init, Last>, *>>,
                cause: Compilable.Cause<Last> = Compilable.Cause.CodeLocation()
            ): EitherNel<InputError, Adjust<Init, Last>> = either {
                ensure(ops.isNotEmpty()) {
                    InputError("Adjust: ops cannot be empty", cause.codeLocations).nel()
                }
                Adjust(from, ops, cause)
            }
        }
    }

    data class Split<Init : Tuple, Last> private constructor(
        override val from: Last,
        override val ops: List<Invalidating<Ap<Init, Last>, *>>,
        override val cause: Compilable.Cause<Last>
    ) : AdjustOrSplit<Init, Last>, Invalidating<Init, Last> {
        companion object {
            operator fun <Init : Tuple, Last> invoke(
                from: Last,
                ops: List<Invalidating<Ap<Init, Last>, *>>,
                cause: Compilable.Cause<Last> = Compilable.Cause.CodeLocation()
            ): EitherNel<InputError, Split<Init, Last>> = either {
                ensure(ops.isNotEmpty()) {
                    InputError("Split: ops cannot be empty", cause.codeLocations).nel()
                }
                Split(from, ops, cause)
            }
        }
    }

    data class Move<Init : Tuple, Last> private constructor(
        val from: Last,
        val to: Ap<Init, Last>,
        val `as`: As<Last>,
        override val cause: Compilable.Cause<Last>
    ) : Invalidating<Init, Last> {
        sealed interface As<out A> {
            object Same : As<Nothing>
            data class New<A>(val data: TypedPayload<A>) : As<A>
        }
        companion object {
            operator fun <Init : Tuple, Last> invoke(
                from: Last,
                to: Ap<Init, Last>,
                `as`: As<Last> = As.Same,
                cause: Compilable.Cause<Last> = Compilable.Cause.CodeLocation()
            ): EitherNel<InputError, Move<Init, Last>> = either {
                Move(from, to, `as`, cause)
            }
        }
    }

    data class MoveRange<Init : Tuple, Last> private constructor(
        val from: Last,
        val fromEnd: Last,
        val to: Ap<Init, Last>,
        val partialNext: PartialNext.Comparator<Last>,
        override val cause: Compilable.Cause<Last>
    ) : Invalidating<Init, Last> {
        companion object {
            operator fun <Init : Tuple, Last> invoke(
                from: Last,
                fromEnd: Last,
                to: Ap<Init, Last>,
                partialNext: PartialNext.Comparator<Last>,
                cause: Compilable.Cause<Last> = Compilable.Cause.CodeLocation()
            ): EitherNel<InputError, MoveRange<Init, Last>> = either {
                ensure(partialNext.compare(from, fromEnd) < 0) {
                    InputError(
                        "MoveRange: fromEnd($fromEnd) must be greater than from($from)",
                        cause.codeLocations
                    ).nel()
                }
                MoveRange(from, fromEnd, to, partialNext, cause)
            }

            operator fun <Init : Tuple, Last : PartialNext<Last>> invoke(
                from: Last,
                fromEnd: Last,
                to: Ap<Init, Last>,
                cause: Compilable.Cause<Last> = Compilable.Cause.CodeLocation()
            ): EitherNel<InputError, MoveRange<Init, Last>> =
                invoke(from, fromEnd, to, object : PartialNext.Comparator<Last> {
                    override fun next(a: Last): Last? {
                        return a.next
                    }

                    override fun compare(l: Last, r: Last): Int {
                        return l.compareTo(r)
                    }

                }, cause)

        }
    }

    data class Create<Last> private constructor(
        val to: Last,
        val data: TypedPayload<Last>,
        override val cause: Compilable.Cause<Last>
    ) : Invalidating<Nothing, Last> {

        companion object {
            operator fun <Last> invoke(
                to: Last,
                data: TypedPayload<Last>,
                cause: Compilable.Cause<Last> = Compilable.Cause.CodeLocation()
            ): EitherNel<InputError, Create<Last>> = Create(to, data, cause).right()
        }
    }
}


val <Last> SubOp<*, Last>.from: Set<Last>
    get() = when (this) {
        is SubOp.Adjust<*, Last> -> setOf(from)
        is SubOp.Create<Last> -> emptySet()
        is SubOp.Move<*, Last> -> setOf(from)
        is SubOp.MoveRange<*, Last> ->
            generateSequence(from) { it
                if (partialNext.compare(it, fromEnd) >= 0) {
                    null
                } else {
                    partialNext.next(it)
                }
            }.toHashSet()
        is SubOp.Split<*, Last> -> setOf(from)
    }


val <Last> SubOp<*, Last>.to: Set<Last>
    get() = getToWithInit(Tuple.Empty).mapTo(HashSet()) { it.last }

private fun <Init : Tuple, Last> SubOp<Init, Last>.getToWithInit(
    init: Init
): Set<Ap<Init, Last>> = when (val op = this) {
    is SubOp.AdjustOrSplit<Init, Last> -> {
        op.ops.flatMapTo(HashSet()) { subOp ->
            subOp.getToWithInit(init.append(op.from)).map { it.init.narrow() }
        }
    }
    is SubOp.Create<Last> -> setOf(init.append(op.to))
    is SubOp.Move<Init, Last> -> setOf(to)
    is SubOp.MoveRange<Init, Last> -> generateSequence(Pair(from, to)) { (from, to) ->
        if (partialNext.compare(from, fromEnd) >= 0) {
            null
        } else {
            val nextFrom = partialNext.next(from) ?: return@generateSequence null
            val nextTo = partialNext.next(to.last) ?: return@generateSequence null
            Pair(nextFrom, to.init.append(nextTo))
        }
    }.map { init.append(it.second.last) }.toHashSet()
}
