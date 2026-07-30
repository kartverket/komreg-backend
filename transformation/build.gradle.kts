plugins {
    id("komreg.kotlin-common")
    `java-library`
}

dependencies {
    api(project(":lib:komreg-param-compat"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(kotlin("reflect"))

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.assertk)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.extensions.arrow)
}
