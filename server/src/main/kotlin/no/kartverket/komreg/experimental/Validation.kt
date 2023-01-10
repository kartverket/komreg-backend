package no.kartverket.komreg.experimental

import arrow.core.Nel
import arrow.core.nel

import arrow.typeclasses.Monoid
import kotlinx.collections.immutable.plus

sealed interface Validation<out A> : no.kartverket.komreg.experimental.LoggingContext<Validation<A>> {
    val log: kotlinx.collections.immutable.PersistentList<no.kartverket.komreg.experimental.LoggingContext.LogRecord>
    fun <B> map(f: (A) -> B) : Validation<B>
    fun <B> flatMap(f: (A) -> Validation<B>) : Validation<B> = TODO()
    fun <B> mapWithLog(f: no.kartverket.komreg.experimental.LoggingContext<Unit>.(A) -> B) : Validation<B>
    fun alsoWithLog(f: no.kartverket.komreg.experimental.LoggingContext<Unit>.(A) -> Unit) : Validation<A> = mapWithLog {
            f(it)
            it
        }
    fun <B> fold(fe: (kotlinx.collections.immutable.PersistentList<no.kartverket.komreg.experimental.LoggingContext.LogRecord>, Nel<no.kartverket.komreg.experimental.LoggingContext.LogRecord>) -> B, fa: (kotlinx.collections.immutable.PersistentList<no.kartverket.komreg.experimental.LoggingContext.LogRecord>, A) -> B) : B
    fun <B, C>productMap(b: Validation<B>, f: (A, B) -> C): Validation<C>
    fun  orNull() : A?

    companion object {
        inline fun <A, B, R> productMap(fa: Validation<A>, fb: Validation<B>, crossinline f: (A, B) -> R) : Validation<R> =
            when (fa) {
                is no.kartverket.komreg.experimental.Valid -> when (fb) {
                    is no.kartverket.komreg.experimental.Valid -> no.kartverket.komreg.experimental.Valid(
                        f(
                            fa.value,
                            fb.value
                        ), fa.log + fb.log
                    )
                    is no.kartverket.komreg.experimental.Invalid -> no.kartverket.komreg.experimental.Invalid(
                        fb.errors,
                        fa.log + fb.log
                    )
                }

                is no.kartverket.komreg.experimental.Invalid -> when (fb) {
                    is no.kartverket.komreg.experimental.Valid -> no.kartverket.komreg.experimental.Invalid(
                        fa.errors,
                        fa.log + fb.log
                    )
                    is no.kartverket.komreg.experimental.Invalid -> no.kartverket.komreg.experimental.Invalid(
                        fa.errors + fb.errors,
                        fa.log + fb.log
                    )
                }
            }

        inline fun <A, B, C, R> productMap(fa: Validation<A>, fb: Validation<B>, fc: Validation<C>, crossinline f: (A, B, C) -> R) : Validation<R> =
            productMap(productMap(fa, fb) { a, b -> { c: C -> f(a,b,c)} }, fc) { ff, c -> ff(c)}
        inline fun <A, B, C, D, R> productMap(fa: Validation<A>, fb: Validation<B>, fc: Validation<C>, fd: Validation<D>, crossinline f: (A, B, C, D) -> R) : Validation<R> =
            productMap(productMap(fa, fb, fc) { a, b, c -> { d: D -> f(a,b,c,d) }},fd) { ff, d -> ff(d) }
        inline fun <A, B, C, D, E_, R> productMap(fa: Validation<A>, fb: Validation<B>, fc: Validation<C>, fd: Validation<D>, fe: Validation<E_>, crossinline f: (A, B, C, D, E_) -> R) : Validation<R> =
            productMap(productMap(fa, fb, fc, fd) { a, b, c, d -> { e: E_ -> f(a,b,c,d,e) }},fe) { ff, e -> ff(e) }
        inline fun <A, B, C, D, E, F, R> productMap(fa: Validation<A>, fb: Validation<B>, fc: Validation<C>, fd: Validation<D>, fe: Validation<E>, ff: Validation<F>, crossinline f: (A, B, C, D, E, F) -> R) : Validation<R> =
            productMap(productMap(fa, fb, fc, fd, fe) { a, b, c, d, e -> { f: F -> f(a,b,c,d,e,f) }},ff) { fff, f1 -> fff(f1) }


        fun <A> valid(a: A): no.kartverket.komreg.experimental.Valid<A> =
            no.kartverket.komreg.experimental.Valid(a, kotlinx.collections.immutable.persistentListOf())
        fun <A> invalid(err: no.kartverket.komreg.experimental.LoggingContext.LogRecord) =
            no.kartverket.komreg.experimental.Invalid<A>(err.nel(), kotlinx.collections.immutable.persistentListOf())
        fun <A> invalid(level: System.Logger.Level, msg: String, throwable: Throwable? = null) = invalid<A>(
            LoggingContext.LogRecord(
                level,
                msg,
                throwable
            )
        )
        fun <A> invalid(level: System.Logger.Level, throwable: Throwable? = null, msgSupplier: () -> String) = invalid<A>(
            LoggingContext.LogRecord(
                level,
                msgSupplier(),
                throwable
            )
        )

        fun <A> monoid(valueCombiner: Monoid<A>) : Monoid<Validation<A>> = object : Monoid<Validation<A>> {
            override fun empty(): Validation<A> = valid(valueCombiner.empty())

            override fun Validation<A>.combine(b: Validation<A>): Validation<A> =
                productMap(b) { aVal, bVal -> with(valueCombiner) { aVal.combine(bVal)} }

        }
    }
}