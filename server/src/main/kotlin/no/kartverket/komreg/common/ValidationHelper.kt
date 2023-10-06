package no.kartverket.komreg.common

import no.kartverket.komreg.common.validation.ValidationType

class ValidationHelper {

    companion object {

        fun validateNotEmpty(
            validationErrors: MutableMap<String, ValidationType>,
            propertyName: String,
            value: List<Any>
        ) {
            if (value.isEmpty()) {
                validationErrors[propertyName] = ValidationType.EMPTY_LIST
            }
        }

    }

}

