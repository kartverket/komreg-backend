import dev.monosoul.jooq.GenerateJooqClassesTask

plugins {
    id("komreg.kotlin-common")
    id("dev.monosoul.jooq-docker") version "9.0.8"
    application
    alias(libs.plugins.ktor.plugin)
    alias(libs.plugins.kotlin.serialization)
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
        onlyCommented = false
    }
}

application {
    mainClass = "no.kartverket.komreg.regulering.api.server.ReguleringApiServerKt"
}

repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation(project(":lib:regulering-dto"))

    implementation(libs.kotlinx.coroutines.core)

    // Ktor dependencies
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.opentelemetry.ktor)
    implementation(libs.ktor.server.khealth)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.openapi)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.server.cors)
    
    // jOOQ dependencies
    implementation(libs.jooq)
    implementation(libs.jooq.kotlin.coroutines)
    jooqCodegen(libs.postgresql)

    // Database dependencies
    implementation(libs.r2dbc.postgresql)
    implementation(libs.r2dbc.pool)
    runtimeOnly(libs.postgresql)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgres)

    // Telemetry dependencies
    implementation(platform(libs.opentelemetry.bom.alpha))
    runtimeOnly(libs.opentelemetry.sdk.core)
    implementation(libs.opentelemetry.sdk.extension.autoconfigure)
    implementation(libs.opentelemetry.runtime.telemetry)
    runtimeOnly(libs.opentelemetry.logback.mdc)
    implementation(libs.opentelemetry.semconv)
    runtimeOnly(libs.opentelemetry.exporter.otlp)

    // Logging dependencies
    implementation(libs.logback.classic)
    runtimeOnly(libs.logstash.logback.encoder)
    implementation(libs.slf4j.jul.to.slf4j)
}

tasks.named<GenerateJooqClassesTask>("generateJooqClasses") {
    basePackageName = "no.kartverket.komreg.regulering.api.server.persistence.jooq"
    schemas = hashSetOf("public")
    outputSchemaToDefault.add("public")
    usingJavaConfig {
        name = "org.jooq.codegen.KotlinGenerator"
        generate.apply {
            withPojos(false)
            withKotlinNotNullRecordAttributes(true)
        }
    }
}
