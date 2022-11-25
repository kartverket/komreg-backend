package no.kartverket.komreg.experimental

import kotlinx.collections.immutable.persistentListOf

sealed interface LoggingContext<out R> {
    fun log(level: System.Logger.Level, throwable: Throwable? = null, msg: String) : R =
        log(persistentListOf(LogRecord(level, msg, throwable)))
    fun log(level: System.Logger.Level, throwable: Throwable? = null, msgSupplier: () -> String): R =
        log(level, throwable, msgSupplier())
    fun log(logRecord: LogRecord): R = log(persistentListOf(logRecord))
    fun log(log: Iterable<LogRecord>) : R
    data class LogRecord(val level: System.Logger.Level, val message: String, val throwable: Throwable? = null)
    class Mutable : LoggingContext<Unit> {
        private val log: MutableList<LogRecord> = mutableListOf()
        override fun log(log: Iterable<LogRecord>) {
            this.log += log
        }
    }
}