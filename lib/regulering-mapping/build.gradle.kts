plugins {
    id("komreg.kotlin-common")
    `java-library`
}

dependencies {
    api(project(":lib:regulering-dto"))
    api(project(":lib:transformation"))

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.runner)
}
