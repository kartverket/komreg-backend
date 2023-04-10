plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":core-api"))
    runtimeOnly("com.oracle.database.jdbc:ojdbc11:23.2.0.0")
}
