import org.apache.tools.ant.filters.ReplaceTokens

group = "no.kartverket.komreg"
version = "1.0-SNAPSHOT"

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
    application
    `jvm-test-suite`
}

repositories {
    maven {
        url = uri("https://maven.pkg.github.com/kartverket/komreg-backend")
        val ghPackagesToken = project.findProperty("GH_PACKAGES_PAT") as String? ?: System.getenv("GH_PACKAGES_PAT")
        if (!ghPackagesToken.isNullOrBlank()) {
            credentials(HttpHeaderCredentials::class) {
                name = "Authorization"
                value = "Bearer $ghPackagesToken"
            }
            authentication {
                create("header", HttpHeaderAuthentication::class)
            }
        } else {
            logger.warn("GH_PACKAGES_PAT er ikke satt: Kan ikke hente avhengigheter fra GitHub Packages")
        }
    }
}

application {
    mainClass.set("no.kartverket.komreg.ApplicationKt")
    applicationName = "komreg-server"
}

@Suppress("UnstableApiUsage")
testing {
    suites {
        withType<JvmTestSuite> {
            useJUnitJupiter()
        }

        val integrationTest by registering(JvmTestSuite::class) {
            dependencies {
                implementation(project.dependencies.create(project))
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(tasks.named("test"))
                    }
                }
            }
        }

        tasks.named("check").configure {
            dependsOn(integrationTest)
        }
    }
}

val scripts by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[scripts.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[scripts.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

tasks.register<JavaExec>("generateTestRegulering") {
    group = "scripts"
    description = "Generates a test regulering and inserts it into the local database"
    classpath = scripts.runtimeClasspath
    mainClass.set("no.kartverket.komreg.scripts.GenerateTestReguleringKt")
}

val integrationTestImplementation by configurations.getting

dependencies {
    implementation(project(":core-api"))
    implementation(project(":transformation"))

    // Skulle egentlig vært runtimeOnly, men har ikke funnet på noe ordentlig grensesnitt for SSR-generering
    implementation(libs.matrikkel.komreg) {
        exclude(group = "com.oracle.database.jdbc")
    }
    implementation(libs.netty.codec.http2)

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

    implementation(libs.dotenv.kotlin)

    implementation(libs.logstash.logback.encoder)

    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.hikari)
    implementation(libs.logback.classic.db)

    runtimeOnly(libs.flyway.postgres)

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


val buildDockerContext = tasks.register<Sync>("buildDockerContext") {
    val dockerfileTemplate = "template.Dockerfile"
    val startScriptPath = tasks
        .named<CreateStartScripts>(ApplicationPlugin.TASK_START_SCRIPTS_NAME)
        .map { "${it.executableDir}/${it.applicationName}" }

    inputs.property("startScriptPath", startScriptPath)

    destinationDir = layout.buildDirectory.get().dir("docker").asFile

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
