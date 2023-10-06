package no.kartverket.komreg.common.validation

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import no.kartverket.komreg.routes.Regulering

class ReguleringValidator {

    companion object {
        fun ensureValidRegulering(body: String): List<String> {
            val errors = mutableListOf<String>()

            try {
                Json.decodeFromString<Regulering>(body)

            } catch (e: SerializationException) {
                errors.add("Invalid JSON format: ${e.message}")
            }

            return errors
        }
    }

}