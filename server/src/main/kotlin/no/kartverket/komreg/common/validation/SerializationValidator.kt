package no.kartverket.komreg.common.validation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class SerializationValidator {

    companion object {
        inline fun <reified T> ensureValid(body: String, serializer: KSerializer<T>): List<String> {
            val errors = mutableListOf<String>()

            try {
                Json.decodeFromString(serializer, body)
            } catch (e: SerializationException) {
                val exceptionType = e::class.simpleName ?: "UnknownException"
                val message = e.message ?: "Unknown error"
                errors.add("$exceptionType: $message")
            }

            return errors
        }
    }

}