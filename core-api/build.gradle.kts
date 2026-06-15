plugins {
    id("komreg.published-library")
}

dependencies {
    api(platform(libs.arrow.stack))
    api(libs.arrow.core)
    api(libs.typesafe.config)
    api(libs.slf4j.api)

    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)

    implementation(kotlin("reflect"))

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.logback.classic)
}
