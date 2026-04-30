package no.kartverket.komreg.core.data

interface PartialNext<A> : Comparable<A> {
    val next: A?

    interface Comparator<A> : kotlin.Comparator<A> {
        fun next(a: A): A?
    }
}