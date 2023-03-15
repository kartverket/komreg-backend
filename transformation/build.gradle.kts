plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":core-api"))
    runtimeOnly(project(":integration-matrikkel"))

    implementation(libs.kotlinx.serialization.json)
}
