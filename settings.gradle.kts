rootProject.name = "komreg-backend"

pluginManagement {

    plugins {
        val kotlinVersion = "2.0.10"

        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("io.ktor.plugin") version "2.3.12"
    }
}

include(
    ":core-api",
    ":transformation",
    ":server",
)
