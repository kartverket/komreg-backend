plugins {
    kotlin("jvm")
    `java-library`
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    // implementation(project(":integration-matrikkel"))
    implementation("com.oracle.database.jdbc:ojdbc11:23.2.0.0")
    api(project(":core-api"))

    // TODO: Denne må hentes fra et sted
    api(files("libs/matrikkel-komreg-4.10-SNAPSHOT.jar"))

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
