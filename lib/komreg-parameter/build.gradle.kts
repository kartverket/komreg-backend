plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core-api"))

    implementation(kotlin("reflect"))
}