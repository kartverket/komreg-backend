package no.kartverket.komreg.transformation

import io.github.cdimascio.dotenv.dotenv

object Config {
    private val env by lazy {
        dotenv {
            ignoreIfMissing = true
            systemProperties = true
        }
    }

    fun get(environmentVariableName: String): String {
        return env[environmentVariableName]
    }
}
