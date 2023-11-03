@file:Suppress("NOTHING_TO_INLINE")

package no.kartverket.komreg.transformation.rule3.util

import arrow.core.raise.Raise

interface PartiallyCombinable<Error, A : PartiallyCombinable<Error, A>> {
    fun Raise<Error>.combine(other: A): A

}
typealias Combinable<A> = PartiallyCombinable<Nothing, A>

fun <A : Combinable<A>> Combinable<A>.combine(other: A): A {
    with(RaiseNothing) {
        return combine(other)
    }
}

private object RaiseNothing : Raise<Nothing> {
    override fun raise(r: Nothing): Nothing = r
}

context (Raise<Error>)
inline fun <Error : Combinable<Error>, A : PartiallyCombinable<Error, A>> A.combine(other: A): A {
    return this@Raise.combine(other)
}

context (Raise<Error>)
inline fun <Error : Combinable<Error>, A : PartiallyCombinable<Error, A>> A.combineAsCreateRules(other: A): A {
    return combine(other)
}