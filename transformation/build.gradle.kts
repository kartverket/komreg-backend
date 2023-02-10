plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":core-api"))
    runtimeOnly(project(":integration-matrikkel"))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
}
