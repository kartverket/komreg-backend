import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") apply false
}

subprojects {
    group = "no.kartverket.komreg"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/kartverket/matrikkel")
            credentials {
                username = project.findProperty("GH_USERNAME") as String? ?: System.getenv("GH_USERNAME")
                password = project.findProperty("GH_PACKAGES_PAT") as String? ?: System.getenv("GH_PACKAGES_PAT")
            }
        }
    }

    plugins.withType<JavaBasePlugin> {
        val javaVersion = JavaVersion.VERSION_11

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = javaVersion
            targetCompatibility = sourceCompatibility
        }

        tasks.withType<KotlinCompile> {
            kotlinOptions.jvmTarget = javaVersion.majorVersion
        }
    }
}


