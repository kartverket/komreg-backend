plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":core-api"))

    // TODO: Denne må hentes fra et sted
    api(files("libs/matrikkel-komreg-4.10-SNAPSHOT.jar"))
}
