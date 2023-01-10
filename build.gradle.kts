import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") apply(false)
}

subprojects {
    group = "no.kartverket.komreg"
    version = "0.1.0-SNAPSHOT"

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