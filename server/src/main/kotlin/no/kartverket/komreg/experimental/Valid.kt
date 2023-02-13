package no.kartverket.komreg.experimental

import arrow.core.Nel
import arrow.typeclasses.Monoid
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentList

data class Valid<A>(val value: A, override val log: PersistentList<LoggingContext.LogRecord> = persistentListOf()) :
    Validation<A> {
        override fun <B> map(f: (A) -> B): Valid<B> = Valid(f(value), log)

        override fun <B, C> productMap(b: Validation<B>, f: (A, B) -> C): Validation<C> = when(b) {
                is Valid -> Valid(f(value, b.value))
                is Invalid -> Invalid(b.errors, log + b.log)
            }

        override fun <B> mapWithLog(f: LoggingContext<Unit>.(A) -> B): Validation<B> {
            val loggingContext = LoggingContext.Mutable()
            return map { f.invoke(loggingContext, value) }
        }

        override fun <B> fold(
            fe: (PersistentList<LoggingContext.LogRecord>, Nel<LoggingContext.LogRecord>) -> B,
            fa: (PersistentList<LoggingContext.LogRecord>, A) -> B
        ): B = fa(log, value)

        override fun orNull(): A = value

        override fun log(log: Iterable<LoggingContext.LogRecord>): Valid<A> {
            return Valid(value, log.toPersistentList())
        }
        companion object {
            fun <A> monoid(valueCombiner: Monoid<A>) : Monoid<Valid<A>> = object : Monoid<Valid<A>> {
                override fun empty(): Valid<A> = Valid(valueCombiner.empty())

                override fun Valid<A>.combine(b: Valid<A>): Valid<A> = with(valueCombiner) {
                    Valid(value.combine(b.value), log + b.log)
                }
            }
        }
    }