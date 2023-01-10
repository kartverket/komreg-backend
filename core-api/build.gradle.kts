plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(platform("io.arrow-kt:arrow-stack:1.1.3"))
    api("io.arrow-kt:arrow-core")
    api("com.typesafe:config:1.4.2")


    //implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.5")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}