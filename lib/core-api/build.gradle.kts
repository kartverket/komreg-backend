import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("komreg.kotlin-common")
    id("komreg.published-library")
    alias(libs.plugins.kotlin.serialization)
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


tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        languageVersion = KotlinVersion.KOTLIN_2_2
        apiVersion = KotlinVersion.KOTLIN_2_2
    }
}

tasks.withType<JavaCompile> {
    options.release.set(17)
}
