plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    implementation(project(":integration-matrikkel"))
    implementation("com.oracle.database.jdbc:ojdbc11:21.9.0.0")
    api(project(":core-api"))

    // TODO: Denne må hentes fra et sted
    // api(files("libs/matrikkel-komreg-4.10-SNAPSHOT.jar"))
}
