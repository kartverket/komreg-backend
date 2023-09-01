package no.kartverket.komreg.transformation.rule

import arrow.core.Either
import no.kartverket.komreg.transformation.Transform
import no.kartverket.komreg.transformation.error.TransformError

sealed interface Rule<in A> {
    fun transform(values: Iterable<Comparable<*>>): Either<TransformError, List<Transform<*>>>
}

