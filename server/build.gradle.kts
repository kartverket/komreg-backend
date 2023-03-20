import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.kartverket.komreg"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":transformation"))

    implementation(libs.kotlin.reflect)

    implementation(libs.kotlinx.cli)

    implementation(libs.kotlinx.coroutines.jdk9)
    implementation(libs.kotlinx.collections.immutable.jvm)

    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.arrow.fx.coroutines)
    implementation(libs.arrow.fx.stm)

    implementation("com.oracle.database.jdbc:ojdbc11:21.9.0.0")
    implementation("org.rocksdb:rocksdbjni:7.10.2")

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.cio)

    implementation(libs.ktor.server.cors.jvm)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.spek.dsl.jvm)
    testRuntimeOnly(libs.spek.runner.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.hamcrest)
    testImplementation(libs.hamcrest.library)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClass.set("MainKt")
}
