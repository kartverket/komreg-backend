plugins {
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.serialization") version "1.8.0"
}

dependencies {
    api(platform("io.arrow-kt:arrow-stack:1.1.3"))
    api("io.arrow-kt:arrow-core")
    api("com.typesafe:config:1.4.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation(platform("com.google.cloud:libraries-bom:26.4.0"))
    implementation("com.google.cloud:google-cloud-secretmanager")
}
