import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.tasks.Sync
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.kotlin.dsl.*
import java.io.File

plugins {
    application
}

val buildDockerContext = tasks.register<Sync>("buildDockerContext") {
    val dockerfileTemplate = "template.Dockerfile"
    val startScriptPath = tasks
        .named(ApplicationPlugin.TASK_START_SCRIPTS_NAME, CreateStartScripts::class)
        .map { "${it.executableDir}/${it.applicationName}" }

    inputs.property("startScriptPath", startScriptPath)

    destinationDir = layout.buildDirectory.get().dir("docker").asFile

    from(File(projectDir, dockerfileTemplate)) {
        rename(dockerfileTemplate, "Dockerfile")
        filter<ReplaceTokens>("tokens" to mapOf(
            "START_SCRIPT" to startScriptPath.get(),
            "MAIN_CLASS" to application.mainClass.get()
        ))
    }

    into("app") {
        from(tasks.named("installDist", Sync::class))
    }


}

tasks.named("assemble") {
    dependsOn(buildDockerContext)
}
