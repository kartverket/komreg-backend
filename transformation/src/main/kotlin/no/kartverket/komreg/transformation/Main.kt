package no.kartverket.komreg.transformation

import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.integration.spi.Transformation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger(object {}::class.java)

suspend fun main() {
    executeSimpleRun()
}

fun getEnvironment(): String = System.getenv("environment") ?: "local"

suspend fun executeSimpleRun(): List<Transformation> {
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.load("reference-${getEnvironment()}.conf")
        }
    }
    val entitySources = EntitySourceManager(bootContext)
    val result = entitySources
        .buildEntityFlow()
        .map {
            Transformation(
                id = it.id,
                transformationType = "None",
                transformedIdent = it.ident ?: emptyMap<Any, Any?>(),
                transformedAssociatedIdents = it.associatedIdents,
                sourceObject = it.sourceObject
            )
        }
        .onEach { logger.info(it.toString()) }

    return result.toList()
}
