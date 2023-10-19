import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.kartverket.komreg"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm")
    application
    id("io.ktor.plugin")
}

allprojects {
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
}

subprojects {
    group = "no.kartverket.komreg"
    version = "1.0-SNAPSHOT"

    afterEvaluate {
        // TODO: Antagelig ikke en god ide å gjøre dette her
        dependencies {
            implementation(libs.logback.classic)
        }
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
