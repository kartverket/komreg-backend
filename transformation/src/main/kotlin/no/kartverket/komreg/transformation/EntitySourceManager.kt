package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.integration.spi.EntitySource
import no.kartverket.komreg.integration.spi.EntitySourceFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ServiceLoader

class EntitySourceManager(bootContext: KrAppBootContext) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    var entitySources: List<EntitySource>

    private var isLocal = false

    init {
        val services = ServiceLoader.load(EntitySourceFactory::class.java)
        logger.info("Fant ${services.toList().size} kilder")

        entitySources = with(bootContext) {
            services.map { service -> with(service) { create() } }
        }

        isLocal = System.getenv("environment") == "local" || System.getenv("environment") == null
        logger.info("Current environment: ${System.getenv("environment")}")

        if (isLocal) {
            entitySources = entitySources.reversed()
        }

        entitySources.forEach {
            logger.info("EntitySource: ${it.id} - $it")
        }
    }
}
