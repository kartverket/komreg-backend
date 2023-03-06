plugins {
    kotlin("jvm")
    `java-library`
}


dependencies {
    api(project(":core-api"))
    runtimeOnly("com.oracle.database.jdbc:ojdbc11:21.7.0.0")
}