package no.kartverket.komreg.experimental

import java.util.logging.LogRecord

interface Validation<A> {
    data class Valid<A>(val a: A) : Validation<A>
    data class Invalid<A>(val err: LogRecord) : Validation<A>
    companion object {
        fun <A> valid(a: A) : Validation<A> = Valid(a)
        fun <A> invalid(err: LogRecord) = Invalid<A>(err)
    }
}