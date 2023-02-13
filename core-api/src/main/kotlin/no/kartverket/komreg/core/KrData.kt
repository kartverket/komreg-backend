package no.kartverket.komreg.core

typealias KrData<A> = Chronicle<LogEntry, DataError, A>

interface DataError {
    val severity: Severity.Fatal
    val message: String
    fun toWarning(severity: Severity.NonFatal = Severity.NonFatal.ERROR): LogEntry

    data class PlainText(
        override val severity: Severity.Fatal,
        override val message: String,
    ) : DataError {
        constructor(message: String) : this(Severity.Fatal.FATAL, message)

        override fun toWarning(severity: Severity.NonFatal): LogEntry =
            LogEntry.PlainText(severity, message)
    }

    companion object {
        operator fun invoke(message: String): DataError = PlainText(message)
    }
}

fun <A> A?.dataErrorIfNull(message: String): Chronicle<Nothing, DataError, A> = this
    ?.asSuccess()
    ?: DataError(message).asFailure()
