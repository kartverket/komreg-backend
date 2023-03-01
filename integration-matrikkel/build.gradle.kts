plugins {
    kotlin("jvm")
    `java-library`
}

val logbackVersion = "1.4.5"


dependencies {
    api(project(":core-api"))
    runtimeOnly("com.oracle.database.jdbc:ojdbc11:21.7.0.0")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

}