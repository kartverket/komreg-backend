group = "no.kartverket.komreg"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
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
    implementation(libs.kotlinx.datetime)

    implementation(libs.arrow.fx.coroutines)
    implementation(libs.arrow.fx.stm)

    implementation("com.oracle.database.jdbc:ojdbc11:23.2.0.0")
    implementation("org.rocksdb:rocksdbjni:8.3.2")

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.cio)

    implementation(libs.ktor.server.cors.jvm)

    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.micrometer.registry.prometheus)
    implementation("org.postgresql:postgresql:42.6.0")
    implementation("org.flywaydb:flyway-core:9.21.1")

    implementation("org.jetbrains.exposed:exposed-core:0.42.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.42.1")
    runtimeOnly("org.jetbrains.exposed:exposed-jdbc:0.42.1")

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.spek.dsl.jvm)
    testRuntimeOnly(libs.spek.runner.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.hamcrest)
    testImplementation(libs.hamcrest.library)
    testImplementation(libs.kotest.runner)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
}
