plugins {
    id("komreg.kotlin-common")
    `java-library`
}

repositories {
    maven {
        url = uri("https://maven.pkg.github.com/kartverket/matrikkel")
        val ghPackagesToken = project.findProperty("GH_PACKAGES_PAT") as String? ?: System.getenv("GH_PACKAGES_PAT")
        if (!ghPackagesToken.isNullOrBlank()) {
            credentials(HttpHeaderCredentials::class) {
                name = "Authorization"
                value = "Bearer $ghPackagesToken"
            }
            authentication {
                create("header", HttpHeaderAuthentication::class)
            }
        } else {
            logger.warn("GH_PACKAGES_PAT er ikke satt: Kan ikke hente avhengigheter fra GitHub Packages")
        }
    }
}

dependencies {
    api(project(":lib:core-api"))
    api(libs.matrikkel.komreg) {
        exclude(group = "com.oracle.database.jdbc")
    }

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.assertk)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.extensions.arrow)
}
