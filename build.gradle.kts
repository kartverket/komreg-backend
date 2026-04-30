import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
    group = "no.kartverket.komreg"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    plugins.withType<JavaBasePlugin> {
        val javaVersion = JavaVersion.VERSION_17

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = javaVersion
            targetCompatibility = sourceCompatibility
        }

        tasks.withType<KotlinCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(javaVersion.majorVersion))
                freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
            }
        }

        plugins.withType<MavenPublishPlugin> {
            extensions.configure<JavaPluginExtension> {
                withSourcesJar()
                withJavadocJar()
            }
        }
    }
}
