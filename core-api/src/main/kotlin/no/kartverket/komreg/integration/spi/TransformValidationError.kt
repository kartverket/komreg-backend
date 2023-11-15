package no.kartverket.komreg.integration.spi

sealed interface TransformValidationError {
    val message: String
    open class ForIdent(val ident: Ident, override val message: String) : TransformValidationError
    data class UncaughtThrowable(val throwable: Throwable) : TransformValidationError {
        override val message: String
            get() = throwable.message ?: throwable::class.qualifiedName ?: "???"
    }
}