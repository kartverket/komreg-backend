group = "no.kartverket.komreg"
version = "0.1.8"

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

dependencies {
    api(platform(libs.arrow.stack))
    api(libs.arrow.core)
    api(libs.typesafe.config)

    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    implementation(platform(libs.google.cloud.libraries))
    implementation(libs.google.cloud.secretmanager)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.kotest.runner)
}

tasks.named("test", Test::class).configure {
    useJUnitPlatform()
}
