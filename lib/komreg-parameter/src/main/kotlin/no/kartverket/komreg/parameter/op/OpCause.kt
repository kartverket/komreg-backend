package no.kartverket.komreg.parameter.op

import arrow.core.NonEmptyList

sealed interface Cause {
    val codeLocations: NonEmptyList<CodeLocation>
}