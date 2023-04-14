rootProject.name = "komreg-backend"

pluginManagement {

    plugins {
        val kotlinVersion = "1.8.21"

        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("io.ktor.plugin") version "2.3.0"
    }
}

include(
    ":core-api",
    ":core-impl",
    ":transformation",
    ":server",
    // ":integration-matrikkel"
)

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("google-cloud", "26.4.0")
            version("kotlinx-cli", "0.3.5")
            version("kotlinx-coroutines", "1.6.4")
            version("kotlinx-serialization", "1.4.1")
            version("mockk", "1.13.2")
            version("junit", "5.9.0")
            version("spek", "2.0.19")
            version("kotest", "5.5.5")
            version("ktor", "2.2.4")
            version("hamcrest", "2.2")
            version("logback", "1.4.5")

            library("kotlin-reflect", "org.jetbrains.kotlin", "kotlin-reflect").withoutVersion()

            library("kotlinx-cli", "org.jetbrains.kotlinx", "kotlinx-cli").versionRef("kotlinx-cli")
            library(
                "kotlinx-collections-immutable-jvm",
                "org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.5"
            )
            library(
                "kotlinx-coroutines-core",
                "org.jetbrains.kotlinx",
                "kotlinx-coroutines-core"
            ).versionRef("kotlinx-coroutines")
            library(
                "kotlinx-coroutines-jdk9",
                "org.jetbrains.kotlinx",
                "kotlinx-coroutines-jdk9"
            ).versionRef("kotlinx-coroutines")
            library(
                "kotlinx-serialization-json",
                "org.jetbrains.kotlinx",
                "kotlinx-serialization-json"
            ).versionRef("kotlinx-serialization")
            library(
                "kotlinx-serialization-protobuf",
                "org.jetbrains.kotlinx",
                "kotlinx-serialization-protobuf"
            ).versionRef("kotlinx-serialization")

            library("google-cloud-libraries", "com.google.cloud", "libraries-bom").versionRef("google-cloud")
            library("google-cloud-secretmanager", "com.google.cloud", "google-cloud-secretmanager").withoutVersion()

            library("ktor-server-core", "io.ktor", "ktor-server-core").versionRef("ktor")
            library("ktor-server-netty", "io.ktor", "ktor-server-netty").versionRef("ktor")
            library("ktor-server-cors", "io.ktor", "ktor-server-cors").versionRef("ktor")
            library("ktor-server-metrics-micrometer", "io.ktor", "ktor-server-metrics-micrometer").versionRef("ktor")
            library("ktor-server-content-negotiation", "io.ktor", "ktor-server-content-negotiation").versionRef("ktor")
            library("ktor-serialization-kotlinx-json", "io.ktor", "ktor-serialization-kotlinx-json").versionRef("ktor")
            library("ktor-server-websockets", "io.ktor", "ktor-server-websockets").versionRef("ktor")
            library("ktor-client-websockets", "io.ktor", "ktor-client-websockets").versionRef("ktor")
            library("ktor-client-cio", "io.ktor", "ktor-client-cio").versionRef("ktor")
            library("ktor-server-cors-jvm", "io.ktor", "ktor-server-cors-jvm").versionRef("ktor")

            library("arrow-stack", "io.arrow-kt", "arrow-stack").version("1.1.3")
            library("arrow-core", "io.arrow-kt", "arrow-core").withoutVersion()
            library("arrow-fx-coroutines", "io.arrow-kt", "arrow-fx-coroutines").withoutVersion()
            library("arrow-fx-stm", "io.arrow-kt", "arrow-fx-stm").withoutVersion()

            library("typesafe-config", "com.typesafe:config:1.4.2")

            library("junit-jupiter-api", "org.junit.jupiter", "junit-jupiter-api").versionRef("junit")
            library("junit-jupiter-engine", "org.junit.jupiter", "junit-jupiter-engine").versionRef("junit")

            library("spek-dsl-jvm", "org.spekframework.spek2", "spek-dsl-jvm").versionRef("spek")
            library("spek-runner-junit5", "org.spekframework.spek2", "spek-runner-junit5").versionRef("spek")

            library("kotest-runner", "io.kotest", "kotest-runner-junit5").versionRef("kotest")

            library("mockk", "io.mockk", "mockk").versionRef("mockk")

            library("hamcrest", "org.hamcrest", "hamcrest").versionRef("hamcrest")
            library("hamcrest-library", "org.hamcrest", "hamcrest-library").versionRef("hamcrest")

            library("logback-classic", "ch.qos.logback", "logback-classic").versionRef("logback")
        }
    }
}
