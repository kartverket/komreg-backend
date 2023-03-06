rootProject.name = "komreg-backend"

pluginManagement {

    plugins {
        val kotlinVersion = "1.8.0"

        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
    }
}

include(
    ":core-api",
    ":integration-matrikkel",
    ":core-impl",
    ":transformation",
    ":server",
)
