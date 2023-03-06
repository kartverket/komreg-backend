package no.kartverket.komreg.core

interface LogEntry {
    val severity: Severity.NonFatal
    val message: String

    data class PlainText(
        override val severity: Severity.NonFatal,
        override val message: String,
    ) : LogEntry
}

fun <F> LogContext<F, LogEntry>.log(severity: Severity.NonFatal, message: String): F =
    appendLog(LogEntry.PlainText(severity, message))

fun <F> LogContext<F, LogEntry>.trace(message: String): F =
    log(Severity.NonFatal.TRACE, message)

fun <F> LogContext<F, LogEntry>.debug(message: String): F =
    log(Severity.NonFatal.DEBUG, message)

fun <F> LogContext<F, LogEntry>.info(message: String): F =
    log(Severity.NonFatal.INFO, message)

fun <F> LogContext<F, LogEntry>.warning(message: String): F =
    log(Severity.NonFatal.WARNING, message)

fun <F> LogContext<F, LogEntry>.error(message: String): F =
    log(Severity.NonFatal.ERROR, message)
