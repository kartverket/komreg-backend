plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    api(project(":core-api"))

    implementation("com.oracle.database.jdbc:ojdbc11:23.2.0.0")
    implementation(libs.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    api("no.statkart.matrikkel:matrikkel-komreg:4.12-SNAPSHOT")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.spek.dsl.jvm)
    testRuntimeOnly(libs.spek.runner.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.hamcrest)
    testImplementation(libs.hamcrest.library)
}
