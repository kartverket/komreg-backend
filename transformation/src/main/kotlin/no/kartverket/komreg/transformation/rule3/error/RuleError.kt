package no.kartverket.komreg.transformation.rule3.error

import arrow.core.raise.Raise
import no.kartverket.komreg.transformation.rule3.util.Combinable

sealed class RuleError : Combinable<RuleError> {
    abstract val message: String
}

data class TextRuleError(override val message: String) : RuleError() {
    override fun Raise<Nothing>.combine(other: RuleError): RuleError {
        return when (other) {
            is TextRuleError -> TextRuleError("$message\n${other.message}")
        }
    }
}