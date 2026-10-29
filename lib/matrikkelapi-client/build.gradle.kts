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
        "javaee"("jakarta.xml.bind:jakarta.xml.bind-api:4.0.0") {
            version {
                prefer("4.0.5")
            }
        }
        "javaee"("jakarta.xml.ws:jakarta.xml.ws-api:4.0.0") {
            version {
                prefer("4.0.5")
            }
        }
    }
    "javaee"(platform("jakarta.platform:jakarta.jakartaee-bom:11.0.0"))

    compileOnly("jakarta.annotation:jakarta.annotation-api")
    api("jakarta.xml.bind:jakarta.xml.bind-api")
    api("jakarta.xml.ws:jakarta.xml.ws-api")

    "jaxwsTools"("org.jvnet.jaxb:jaxb-plugins:4.0.16")
    "jaxwsTools"("com.sun.xml.ws:jaxws-tools:4.0.5")
    "jaxwsTools"("org.slf4j:slf4j-simple:1.7.36")
}



val wsdlPath = "$projectDir/src/main/wsdl/"
val wsdlPackage = "no/statkart/matrikkel/matrikkelapi/wsapi/v1/wsdl/"
val generateServiceSource = listOf("StoreService", "EndringsloggService", "MatrikkelenhetService", "KommuneService", "TeigService").map { serviceName ->
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
