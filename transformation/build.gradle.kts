plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    api(project(":core-api"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    api("com.google.guava:guava:32.1.3-jre")
    implementation(kotlin("reflect"))

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.spek.dsl.jvm)
    testRuntimeOnly(libs.spek.runner.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.hamcrest)
    testImplementation(libs.hamcrest.library)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.extensions.arrow)

}
