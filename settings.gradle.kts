rootProject.name = "komreg-backend"

pluginManagement {

    plugins {
        val kotlinVersion = "2.3.10"

        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("io.ktor.plugin") version "3.4.1"
    }
}

include(
    ":core-api",
    ":transformation",
    ":server",
)
