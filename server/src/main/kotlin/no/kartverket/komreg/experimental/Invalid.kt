package no.kartverket.komreg.experimental

import arrow.core.Nel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentList

data class Invalid<A>(val errors: Nel<LoggingContext.LogRecord>, override val log: PersistentList<LoggingContext.LogRecord>) :
    Validation<A> {
    @Suppress("UNCHECKED_CAST")
    override fun <B> map(f: (A) -> B): Invalid<B> = this as Invalid<B>

    @Suppress("UNCHECKED_CAST")
    override fun <B> mapWithLog(f: LoggingContext<Unit>.(A) -> B): Validation<B> = this as Invalid<B>

    override fun <B, C> productMap(b: Validation<B>, f: (A, B) -> C): Invalid<C> = when (b) {
        is Valid -> Invalid(errors, log + b.log)
        is Invalid -> Invalid(errors + b.errors, log + b.log)
    }

    override fun <B> fold(
        fe: (PersistentList<LoggingContext.LogRecord>, Nel<LoggingContext.LogRecord>) -> B,
        fa: (PersistentList<LoggingContext.LogRecord>, A) -> B,
    ): B = fe(log, errors)

    override fun orNull(): A? = null

    override fun log(log: Iterable<LoggingContext.LogRecord>): Invalid<A> {
        log.plus(LoggingContext.LogRecord(System.Logger.Level.ERROR, ""))
        return Invalid(errors, log.toPersistentList())
    }
}
