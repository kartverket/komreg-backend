plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":core-api"))
    runtimeOnly("com.oracle.database.jdbc:ojdbc11:21.9.0.0")
}
