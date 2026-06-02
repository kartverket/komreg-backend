@file:OptIn(ExperimentalRaiseAccumulateApi::class)

package no.kartverket.komreg.parameter.op

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.NonEmptyList
import arrow.core.raise.ExperimentalRaiseAccumulateApi
import arrow.core.raise.RaiseAccumulate
import arrow.core.raise.accumulate
import arrow.core.raise.either
import no.kartverket.komreg.parameter.data.Tuple
import no.kartverket.komreg.parameter.op.SubOp.*

fun <Error, A> RaiseAccumulate<Error>.nullIfError(either: Either<NonEmptyList<Error>, A>): A? =
    either.fold(
        { errs -> errs.forEach { accumulate(it) }; null },
        { op -> op }
    )

operator fun <Init : Tuple, Last : Comparable<Last>> Adjust.Companion.invoke(
    from: Last,
    ops: List<EitherNel<InputError, SubOp<Tuple.Ap<Init, Last>, *>>>,
    cause: Compilable.Cause<Last> = Compilable.Cause.CodeLocation()
): EitherNel<InputError, Adjust<Init, Last>> = either {
    accumulate {
        Adjust(
            from,
            ops.mapNotNull {
                nullIfError(it)
            },
            cause
        ).bindNel()
    }
}

operator fun <Init : Tuple, Last : Comparable<Last>> Split.Companion.invoke(
    from: Last,
    ops: List<EitherNel<InputError, Invalidating<Tuple.Ap<Init, Last>, *>>>,
    cause: Compilable.Cause<Last> = Compilable.Cause.CodeLocation()
): EitherNel<InputError, Split<Init, Last>> = either {
    accumulate {
        Split(
            from,
            ops.mapNotNull {
                nullIfError(it)
            },
            cause
        ).bindNel()
    }
}
