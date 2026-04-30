package no.kartverket.komreg.parameter.op

import arrow.core.NonEmptyList
import arrow.core.NonEmptySet
import arrow.core.nonEmptyListOf
import no.kartverket.komreg.integration.spi.Payload
//import no.kartverket.komreg.parameter.op.LoOp.Cause.Mapped.Companion.map
import no.kartverket.komreg.parameter.data.Tuple

sealed interface LoOp<out A> {
    val cause: Cause<A>
//    fun <B> map(f: (A) -> B): LoOp<B>

    sealed interface Cause<out A> : no.kartverket.komreg.parameter.op.Cause {
        sealed interface Unmapped<out A> : Cause<A>
        class CodeLocation(stackTrace: List<StackTraceElement> = computeStacktrace(2)) :
            no.kartverket.komreg.parameter.op.CodeLocation(stackTrace), Unmapped<Nothing> {
            override val codeLocations: NonEmptyList<no.kartverket.komreg.parameter.op.CodeLocation>
                get() = nonEmptyListOf(this)
        }

        data class SiblingOp<out A : Tuple>(val op: Compilable<A>) : Unmapped<A> {
            override val codeLocations: NonEmptyList<no.kartverket.komreg.parameter.op.CodeLocation>
                get() = op.cause.codeLocations

            override fun toString(): String {
                return "${op::class.simpleName}@${codeLocations.first()}"
            }
        }

        data class MergeC<A : Tuple, X>(val op: Merge<A, X>) : Unmapped<Tuple.Ap<A, X>> {
            override val codeLocations: NonEmptyList<no.kartverket.komreg.parameter.op.CodeLocation>
                get() = op.cause.codeLocations

            override fun toString(): String {
                return "${op::class.simpleName}@${codeLocations.first()}"
            }
        }

        data class ImplicitUpdate<out A : Tuple>(val ops: NonEmptySet<LoOp<A>>) : Unmapped<A> {
            override val codeLocations: NonEmptyList<no.kartverket.komreg.parameter.op.CodeLocation>
                get() = ops.flatMap { it.cause.codeLocations }.toNonEmptyList()

            override fun toString(): String {
                return ops.singleOrNull()?.let { op -> "${op::class.simpleName}@${codeLocations.first()}"  } ?:
                ops.map { op -> "${op::class.simpleName}@${codeLocations.first()}" }.joinToString(", ", "[", "]", 3)
            }
        }

//        data class Mapped<out B> private constructor(
//            val cause: Unmapped<Any?>,
//            override val codeLocations: NonEmptyList<CodeLocation>,
//        ) : Cause<B> {
//            companion object {
//                fun <A, B> Cause<A>.map(f: (A) -> B): Cause<B> = when (this) {
//                    is Unmapped -> Mapped(this, CodeLocation().nel())
//                    is Mapped -> Mapped(this.cause, CodeLocation().nel() + this.codeLocations)
//                }
//            }
//        }
    }

    sealed interface SourceOp<out A> : LoOp<A> {
        val from: A
        sealed interface WithSingleTarget<out A> : SourceOp<A> {
            val to: A
        }
    }

    sealed interface TargetOp<out A> : LoOp<A> {
        val to: A
    }

    data class Expire<out A>(
        val to: Set<A>,
        override val from: A,
        override val cause: Cause<A> = Cause.CodeLocation()
    ) : SourceOp<A> {
//        override fun <B> map(f: (A) -> B): Expire<B> {
//            return Expire(f(from), cause.map(f))
//        }
    }

    data class Create<out A>(
        override val to: A,
        val partiallyFrom: Set<A>,
        val data: Payload,
        override val cause: Cause<A> = Cause.CodeLocation()
    ) : TargetOp<A> {
//        override fun <B> map(f: (A) -> B): LoOp<B> {
//            return Create(f(to), data, cause.map(f))
//        }
    }

    data class Move<out A>(
        override val to: A,
        override val from: A,
        override val cause: Cause<A>
    ) : SourceOp.WithSingleTarget<A>, TargetOp<A> {
//        override fun <B> map(f: (A) -> B): Move<B> {
//            return Move(f(to), f(from), cause.map(f))
//        }
    }

    data class MoveChildrenAndExpire<out A>(
        override val to: A,
        override val from: A,
        override val cause: Cause<A>
    ) : SourceOp.WithSingleTarget<A> {
//        override fun <B> map(f: (A) -> B): MoveChildren<B> {
//            return MoveChildren(f(to), f(from), cause.map(f))
//        }
    }

    data class Keep<out A>(
        override val to: A,
        val partiallyFrom: NonEmptySet<A>,
        override val cause: Cause<A> = LoOp.Cause.CodeLocation()
    ) : TargetOp<A> {
//        override fun <B> map(f: (A) -> B): LoOp<B> {
//            return Update(f(to), cause.map(f))
//        }
    }
}