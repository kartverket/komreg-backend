plugins {
    id("komreg.kotlin-common")
    `java-library`
}

dependencies {
    api(project(":lib:core-api"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(kotlin("reflect"))

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.assertk)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.extensions.arrow)
    // ReformSpec brukes til å bygge ReformClaims i testene til ReformClaimsTransformer.
    // :regulering-api:regulering-api-reform har lib:transformation som api-avhengighet, men syklusen er bare test -> main.
    testImplementation(project(":regulering-api:regulering-api-reform"))
}
