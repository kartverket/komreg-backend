import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.*
import java.io.File
import java.io.Serializable

plugins {
    application
}

val dockerContext = extensions.create<DockerContextExtension>("dockerContext").apply {
    tokens.apply {
        put("START_SCRIPT", tasks.named<CreateStartScripts>(ApplicationPlugin.TASK_START_SCRIPTS_NAME).map {  "${it.executableDir}/${it.applicationName}"  })
        put("MAIN_CLASS", application.mainClass)
    }
}

val buildDockerContext = tasks.register<Sync>("buildDockerContext") {
    val dockerfileTemplate = "template.Dockerfile"

    inputs.property("dockerContextTokens", dockerContext.tokens)

    destinationDir = layout.buildDirectory.get().dir("docker").asFile

    val spec =from(File(projectDir, dockerfileTemplate)) {
        rename(dockerfileTemplate, "Dockerfile")
    }

    into("app") {
        from(tasks.named("installDist", Sync::class))
    }

    doFirst {
        spec.filter<ReplaceTokens>("tokens" to dockerContext.tokens.mapValues {
            when (val value = it.value) {
                is Provider<*> -> value.get()
                else -> value
            }.toString()
        })
    }
}

tasks.named("assemble") {
    dependsOn(buildDockerContext)
}

abstract class DockerContextExtension() {
    val tokens: MutableMap<String, Any> = hashMapOf()
}
