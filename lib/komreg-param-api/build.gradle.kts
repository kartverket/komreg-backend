plugins {
    id("komreg.kotlin-common")
}

dependencies {
    api(platform(libs.arrow.stack))
    api(libs.arrow.core)
    api(libs.kotlinx.datetime)

    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.extensions.arrow)
}