package no.kartverket.komreg.parameter.data

interface Enumerator<A : Any> : Comparator<A> {
    fun next(a: A) : A?
    fun prev(a: A) : A?
}