package no.kartverket.komreg.core

interface LogContext<out F, in A> {
    fun appendLog(logEntry: A): F

    data class Mutable<A>(val logEntries: MutableList<A> = mutableListOf() ) : LogContext<Unit, A> {
        override fun appendLog(logEntry: A) {
            logEntries.add(logEntry)
        }
    }
}



