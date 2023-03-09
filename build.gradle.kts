import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.kartverket.komreg"
version = "1.0-SNAPSHOT"

plugins {
    val kotlinVersion = "1.8.0"
    kotlin("jvm") version kotlinVersion
    application
    id("io.ktor.plugin") version "2.2.1"
}

repositories {
    mavenCentral()
}

subprojects {
    group = "no.kartverket.komreg"
    version = "0.1.1"
    apply(plugin = "org.jetbrains.kotlin.jvm")

    val logbackVersion = "1.4.5"

    dependencies {
        implementation("ch.qos.logback:logback-classic:$logbackVersion")
    }

    plugins.withType<JavaBasePlugin> {
        val javaVersion = JavaVersion.VERSION_11

        repositories {
            mavenCentral()
        }

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = javaVersion
            targetCompatibility = sourceCompatibility
        }

        tasks.withType<KotlinCompile> {
            kotlinOptions.jvmTarget = javaVersion.majorVersion
        }
    }
}

dependencies {
    implementation(project(":server"))
}

application {
    mainClass.set("no.kartverket.komreg.ApplicationKt")
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "no.kartverket.komreg.ApplicationKt"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
