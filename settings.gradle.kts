rootProject.name = "komreg-backend"

pluginManagement {

    plugins {
        val kotlinVersion = "2.0.20"

        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("io.ktor.plugin") version "3.0.3"
    }
}

include(
    ":core-api",
    ":transformation",
    ":server",
)
