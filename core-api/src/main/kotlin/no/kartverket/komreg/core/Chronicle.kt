package no.kartverket.komreg.core

import arrow.core.*

/**
 * @param L logged event type
 * @param E failure type
 * @param A success type
 */
sealed interface Chronicle<out L, out E, out A> : LogContext<Chronicle<L, E, A>, @UnsafeVariance L> {

    data class Failures<out L, out E>(
        override val log: List<L>,
        val values: Nel<E>
    ) : Chronicle<L, E, Nothing> {
        override fun appendLog(logEntry: @UnsafeVariance L): Chronicle<L, E, Nothing> =
            Failures(log + logEntry, values)
        override fun <B> map(f: (Nothing) -> B): Chronicle<L, E, B> {
            return this
        }
    }

    data class Success<out L, out A>(
        override val log: List<L>,
        val value: A
    ) : Chronicle<L, Nothing, A> {
        override fun appendLog(logEntry: @UnsafeVariance L): Chronicle<L, Nothing, A> =
            Success(log + logEntry, value)
        override fun <B> map(f: (A) -> B): Chronicle<L, Nothing, B> = Success(log, f(value))
    }

    val log: List<L>
    fun <B> map(f: (A) -> B) : Chronicle<L, E, B>
}

inline fun <L, E, A, Lin : @UnsafeVariance L, Ein : @UnsafeVariance E, B> Chronicle<L, E, A>.flatMap(f: (A) -> Chronicle<Lin, Ein, B>) : Chronicle<L, E, B> = when (this) {
    is Chronicle.Failures -> this
    is Chronicle.Success -> {
        val result = f(this.value)
        when (result) {
            is Chronicle.Failures -> Chronicle.Failures(this.log + result.log, result.values)
            is Chronicle.Success -> Chronicle.Success(this.log + result.log, result.value)
        }
    }
}

fun <A> A.asSuccess(): Chronicle<Nothing, Nothing, A> = Chronicle.Success(emptyList(), this)
fun <E> E.asFailure(): Chronicle<Nothing, E, Nothing> = Chronicle.Failures(emptyList(), this.nel())

inline fun <L, E, A, B, R> productMap(
    fa: Chronicle<L, E, A>,
    fb: Chronicle<L, E, B>,
    crossinline f: (A, B) -> R
): Chronicle<L, E, R> =
    when (fa) {
        is Chronicle.Failures -> when (fb) {
            is Chronicle.Failures -> Chronicle.Failures(fa.log + fb.log, fa.values + fb.values)
            is Chronicle.Success -> Chronicle.Failures(fa.log + fb.log, fa.values)
        }

        is Chronicle.Success -> when (fb) {
            is Chronicle.Failures -> Chronicle.Failures(fa.log + fb.log, fb.values)
            is Chronicle.Success -> Chronicle.Success(fa.log + fb.log, f(fa.value, fb.value))
        }
    }
inline fun <L, E, A, B, C, R> productMap(fa: Chronicle<L, E, A>, fb: Chronicle<L, E, B>, fc: Chronicle<L, E, C>, crossinline f: (A, B, C) -> R) : Chronicle<L, E, R> =
    productMap(productMap(fa, fb) { a, b -> { c: C -> f(a,b,c)} }, fc) { ff, c -> ff(c)}
inline fun <L, E, A, B, C, D, R> productMap(fa: Chronicle<L, E, A>, fb: Chronicle<L, E, B>, fc: Chronicle<L, E, C>, fd: Chronicle<L, E, D>, crossinline f: (A, B, C, D) -> R) : Chronicle<L, E, R> =
    productMap(productMap(fa, fb, fc) { a, b, c -> { d: D -> f(a,b,c,d) }},fd) { ff, d -> ff(d) }
inline fun <L, E, A, B, C, D, E_, R> productMap(fa: Chronicle<L, E, A>, fb: Chronicle<L, E, B>, fc: Chronicle<L, E, C>, fd: Chronicle<L, E, D>, fe: Chronicle<L, E, E_>, crossinline f: (A, B, C, D, E_) -> R) : Chronicle<L, E, R> =
    productMap(productMap(fa, fb, fc, fd) { a, b, c, d -> { e: E_ -> f(a,b,c,d,e) }},fe) { ff, e -> ff(e) }
inline fun <L, E, A, B, C, D, E_, F, R> productMap(fa: Chronicle<L, E, A>, fb: Chronicle<L, E, B>, fc: Chronicle<L, E, C>, fd: Chronicle<L, E, D>, fe: Chronicle<L, E, E_>, ff: Chronicle<L, E, F>, crossinline f: (A, B, C, D, E_, F) -> R) : Chronicle<L, E, R> =
    productMap(productMap(fa, fb, fc, fd, fe) { a, b, c, d, e -> { f: F -> f(a,b,c,d,e,f) }},ff) { fff, f1 -> fff(f1) }



fun <L, E, A> Chronicle<Nothing, E, A>.withLog(log: List<L>): Chronicle<L, E, A> = when (this) {
    is Chronicle.Failures -> Chronicle.Failures(log, this.values)
    is Chronicle.Success -> Chronicle.Success(log, this.value)
}

fun <L, E, A> Chronicle<L, E, A>.useLog(block: LogContext<Unit, L>.(Either<Nel<E>, A>) -> Unit): Chronicle<L, E, A> {
    val value = when (this) {
        is Chronicle.Failures -> this.values.left()
        is Chronicle.Success -> this.value.right()
    }
    val logContext = LogContext.Mutable<L>()
    block(logContext, value)
    return logContext.logEntries.fold(this) { acc, a ->
        acc.appendLog(a)
    }
}

fun <L, E, A> Chronicle<L, E, A>.useLogTap(block: LogContext<Unit, L>.(A) -> Unit): Chronicle<L, E, A> = when (this) {
    is Chronicle.Failures -> this
    is Chronicle.Success -> {
        val logContext = LogContext.Mutable<L>()
        block(logContext, value)
        logContext.logEntries.fold(this as Chronicle<L, E, A>) { acc, a ->
            acc.appendLog(a)
        }
    }
}

fun <E, A> Chronicle<Nothing, E, A>.withLog(): Chronicle<LogEntry, E, A> = withLog(emptyList())
