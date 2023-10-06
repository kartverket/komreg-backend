package no.kartverket.komreg.validators

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import no.kartverket.komreg.routes.Regulering

class ReguleringValidator {

    companion object {
        fun ensureValidRegulering(body: String): List<String> {
            val errors = mutableListOf<String>()

            try {
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString<Regulering>(body)

            } catch (e: SerializationException) {
                errors.add("Invalid JSON format: ${e.message}")
            }

            return errors
        }
    }

}