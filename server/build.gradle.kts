group = "no.kartverket.komreg"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `jvm-test-suite`
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, "seconds")
    }
}

@Suppress("UnstableApiUsage")
testing {
    suites {
        withType<JvmTestSuite> {
            useJUnitJupiter()
        }

        val integrationTest by registering(JvmTestSuite::class) {
            testType.set(TestSuiteType.INTEGRATION_TEST)

            dependencies {
                implementation(project)
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(tasks.named("test"))
                    }
                }
            }

            tasks.named("check").configure {
                dependsOn(sources.classesTaskName)
            }
        }
    }
}

val integrationTestImplementation by configurations.getting

@Suppress("UnstableApiUsage")
dependencies {
    implementation(project(":core-api"))
    implementation(project(":transformation"))

    api("no.statkart.matrikkel:matrikkel-komreg:4.15-SNAPSHOT") {
        isChanging = true
    }
    implementation("io.netty:netty-codec-http2:4.1.100.Final")

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
    implementation("org.rocksdb:rocksdbjni:8.5.4")

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
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.hikari)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.spek.dsl.jvm)
    testRuntimeOnly(libs.spek.runner.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.hamcrest)
    testImplementation(libs.hamcrest.library)
    testImplementation(libs.kotest.runner)

    integrationTestImplementation(libs.testcontainers.core)
    integrationTestImplementation(libs.testcontainers.postgresql)
    integrationTestImplementation(libs.postgresql)
    integrationTestImplementation(libs.flyway.core)
    integrationTestImplementation(libs.hikari)
    integrationTestImplementation(project(":core-api"))
    integrationTestImplementation(libs.kotlinx.serialization.json)

    // TODO: For PoC på uthenting av fordelingsparametre for kommune
    implementation("com.oracle.database.jdbc:ojdbc11:23.2.0.0")
}
