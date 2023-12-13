import org.apache.tools.ant.filters.ReplaceTokens

group = "no.kartverket.komreg"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    `jvm-test-suite`
}

application {
    mainClass.set("no.kartverket.komreg.ApplicationKt")
    applicationName = "komreg-server"
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

    runtimeOnly("no.statkart.matrikkel:matrikkel-komreg:4.15-SNAPSHOT") {
        isChanging = true
        exclude(group = "com.oracle.database.jdbc")
    }
    implementation("io.netty:netty-codec-http2:4.1.102.Final")

    implementation(libs.kotlin.reflect)

    implementation(libs.kotlinx.cli)

    implementation(libs.kotlinx.coroutines.jdk9)
    implementation(libs.kotlinx.collections.immutable.jvm)

    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.arrow.fx.coroutines)
    implementation(libs.arrow.fx.stm)

    runtimeOnly(libs.ojdbc11)
    implementation("org.rocksdb:rocksdbjni:8.8.1")
    implementation(libs.logback.classic)

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
    implementation(libs.logback.classic.db)

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
}


val buildDockerContext = tasks.create<Sync>("buildDockerContext") {
    val dockerfileTemplate = "template.Dockerfile"
    val startScriptPath = tasks
        .named<CreateStartScripts>(ApplicationPlugin.TASK_START_SCRIPTS_NAME)
        .map { "${it.executableDir}/${it.applicationName}" }

    inputs.property("startScriptPath", startScriptPath)

    destinationDir = File(buildDir, "docker")

    from(File(projectDir, dockerfileTemplate)) {
        rename(dockerfileTemplate, "Dockerfile")
        filter<ReplaceTokens>("tokens" to mapOf(
            "START_SCRIPT" to startScriptPath.get())
        )
    }

    into("app") {
        from(tasks.installDist)
    }
}

tasks.assemble {
    dependsOn(buildDockerContext)
}

tasks.distTar {
    enabled = false
}

tasks.distZip {
    enabled = false
}
