rootProject.name = "komreg-backend"

pluginManagement {

    plugins {
        val kotlinVersion = "2.2.0"

        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("io.ktor.plugin") version "3.2.0"
    }
}

include(
    ":core-api",
    ":transformation",
    ":server",
)
