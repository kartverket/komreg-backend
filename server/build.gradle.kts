import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinxCliVersion = "0.3.5"
val kotlinxCoroutinesVersion = "1.6.4"
val mockkVersion = "1.13.2"
val junitVersion = "5.9.0"
val spekVersion = "2.0.19"
val hamcrestVersion = "2.2"

group = "no.kartverket.komreg"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")

    id("org.jlleitschuh.gradle.ktlint") version "11.0.0"
    id("org.jlleitschuh.gradle.ktlint-idea") version "11.0.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core-api"))

    runtimeOnly(project(":core-impl"))
    runtimeOnly(project(":integration-matrikkel"))

    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.jetbrains.kotlinx:kotlinx-cli:$kotlinxCliVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.5")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")


    implementation("io.arrow-kt:arrow-fx-coroutines")
    implementation("io.arrow-kt:arrow-fx-stm")

    implementation("com.oracle.database.jdbc:ojdbc11:21.7.0.0")
    implementation("org.rocksdb:rocksdbjni:7.7.3")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.hamcrest:hamcrest:$hamcrestVersion")
    testImplementation("org.hamcrest:hamcrest-library:$hamcrestVersion")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClass.set("MainKt")
}
