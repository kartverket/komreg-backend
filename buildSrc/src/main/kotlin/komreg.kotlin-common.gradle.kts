import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-base`
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
