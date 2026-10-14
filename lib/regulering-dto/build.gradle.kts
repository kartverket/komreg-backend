plugins {
    id("komreg.kotlin-common")
    `java-library`
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
}
