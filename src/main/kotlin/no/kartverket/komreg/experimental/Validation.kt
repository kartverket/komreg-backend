package no.kartverket.komreg.experimental

import arrow.core.Nel
import arrow.core.foldMap
import arrow.core.nel
import arrow.typeclasses.Monoid
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import java.lang.System.Logger.Level

sealed interface Validation<out A> : LoggingContext<Validation<A>> {
    val log: PersistentList<LoggingContext.LogRecord>
    fun <B> map(f: (A) -> B) : Validation<B>
    fun <B> mapWithLog(f: LoggingContext<Unit>.(A) -> B) : Validation<B>
    fun <B> fold(fe: (PersistentList<LoggingContext.LogRecord>, Nel<LoggingContext.LogRecord>) -> B, fa: (PersistentList<LoggingContext.LogRecord>, A) -> B) : B
    fun <B, C>productMap(b: Validation<B>, f: (A, B) -> C): Validation<C>
    fun  orNull() : A?

    companion object {
        inline fun <A, B, R> productMap(fa: Validation<A>, fb: Validation<B>, crossinline f: (A, B) -> R) : Validation<R> =
            when (fa) {
                is Valid -> when (fb) {
                    is Valid -> Valid(f(fa.value, fb.value), fa.log + fb.log)
                    is Invalid -> Invalid(fb.errors, fa.log + fb.log)
                }

                is Invalid -> when (fb) {
                    is Valid -> Invalid(fa.errors, fa.log + fb.log)
                    is Invalid -> Invalid(fa.errors + fb.errors, fa.log + fb.log)
                }
            }

        inline fun <A, B, C, R> productMap(fa: Validation<A>, fb: Validation<B>, fc: Validation<C>, crossinline f: (A, B,C) -> R) : Validation<R> =
            productMap(productMap(fa, fb) { a, b -> { c: C -> f(a,b,c)} }, fc) { ff, c -> ff(c)}

        inline fun <A, B, C, D, R> productMap(fa: Validation<A>, fb: Validation<B>, fc: Validation<C>, fd: Validation<D>, crossinline f: (A, B,C, D) -> R) : Validation<R> =
            productMap(productMap(fa, fb, fc) { a, b, c -> { d: D -> f(a,b,c,d) }},fd) { ff, d -> ff(d) }


        fun <A> valid(a: A): Validation<A> = Valid(a, persistentListOf())
        fun <A> invalid(err: LoggingContext.LogRecord) = Invalid<A>(err.nel(), persistentListOf())
        fun <A> invalid(level: Level, msg: String, throwable: Throwable? = null) = invalid<A>(
            LoggingContext.LogRecord(
                level,
                msg,
                throwable
            )
        )
        fun <A> invalid(level: Level, throwable: Throwable? = null, msgSupplier: () -> String) = invalid<A>(
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

fun <FFA : Iterable<Validation<A>>, FA: Iterable<A>, A> FFA.toValidation(lift : (A) -> FA, combiner: Monoid<Validation<FA>>): Validation<FA> {
    return foldMap(combiner) { validation ->
        validation.map(lift)
    }
}

fun <FFA : Iterable<Validation<A>>, FA : Iterable<A>, A> FFA.toValid(
    lift: (A) -> FA,
    mfa: Monoid<FA>
): Valid<FA> = foldMap(Valid.monoid(mfa)) { validation ->
    when (validation) {
        is Valid -> validation.map(lift)
        is Invalid -> Valid(mfa.empty(), validation.log + validation.errors)
    }
}

fun <FFA : Iterable<Validation<A>>, A> FFA.toValidList(): Valid<List<A>> = toValid(::listOf, Monoid.list())