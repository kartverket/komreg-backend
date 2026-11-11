plugins {
    id("komreg.kotlin-common")
    alias(libs.plugins.kotlin.serialization)
    `java-library`
}

dependencies {
    api(project(":lib:transformation"))
    api(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertk)
}
