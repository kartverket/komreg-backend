plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

configurations {
    val javaEeConf = create("javaee") {
        isCanBeConsumed = false
        isCanBeResolved = false
    }
    named("api") {
        extendsFrom(javaEeConf)
    }
    named("compileOnlyApi") {
        extendsFrom(javaEeConf)
    }
    create("jaxwsTools") {
        isCanBeConsumed = false
        extendsFrom(configurations["api"], javaEeConf)
    }
}

dependencies {
    constraints {
        "javaee"(libs.jakarta.xml.bind.api)
        "javaee"(libs.jakarta.xml.ws.api)
    }
    "javaee"(platform(libs.jakarta.ee.bom))

    compileOnly("jakarta.annotation:jakarta.annotation-api")
    api("jakarta.xml.bind:jakarta.xml.bind-api")
    api("jakarta.xml.ws:jakarta.xml.ws-api")

    "jaxwsTools"(libs.jvnet.jaxb.plugins)
    "jaxwsTools"(libs.jakarta.xml.ws.tools)
    "jaxwsTools"(libs.jakarta.xml.ws.tools.slf4j) // Velg en kompatibel versjon med ws-tools slf4j-api
}



val wsdlPath = "$projectDir/src/main/wsdl/"
val wsdlPackage = "no/statkart/matrikkel/matrikkelapi/wsapi/v1/wsdl/"
val generateServiceSource = listOf(
    "StoreService", "EndringsloggService", "MatrikkelenhetService",
    "KommuneService", "TeigService", "BygningService", "AdresseService"
    )
    .map { serviceName ->
        tasks.register<JavaExec>("generate${serviceName}Source") {
        val wsimportDir = project.layout.buildDirectory.dir("generated/sources/wsdl2java").get().asFile
        val destinationDir = File(wsimportDir, serviceName)
        description = "Generate bindings for $serviceName"
        inputs.dir(wsdlPath)
        outputs.dir(destinationDir)
        classpath = configurations["jaxwsTools"]
        mainClass.set("com.sun.tools.ws.WsImport")
        args(
            "-s", destinationDir,
            "-Xnocompile",
            "-XdisableAuthenticator",
            "-encoding", "UTF-8",
            "-wsdllocation", "/$wsdlPackage${serviceName}WS.wsdl",
            "-B-no-header",
            "-B-mark-generated",
            "-B-noDate",
            "-B-Xvalue-constructor",
            "-B-Xsetters-mode=direct",
            "-B-XsimpleEquals",
            "-B-XsimpleHashCode",
            "-quiet",
            "$wsdlPath${serviceName}WS.wsdl"
        )
        doFirst {
            destinationDir.listFiles()?.forEach { it.deleteRecursively() }
            destinationDir.mkdirs()
        }
    }
}


tasks.named<ProcessResources>("processResources") {
    from(files(wsdlPath)) {
        into(wsdlPackage)
    }
    into(wsdlPackage) {

    }
}

val generateServiceSources = tasks.register<Sync>("generateServiceSources") {
    description = "Copy generated sources from all services to a single directory"
    for (taskProvider in generateServiceSource) {
        from(taskProvider.map { it.outputs.files.singleFile })
    }
    destinationDir = project.layout.buildDirectory.dir("generated/sources/wsdl2java/merged").get().asFile
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

sourceSets.main.configure {
    java.srcDir(generateServiceSources.map { it.outputs.files.singleFile })
}
