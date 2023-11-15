package no.kartverket.komreg.integration

import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.integration.spi.EntityProcessor
import no.kartverket.komreg.integration.spi.EntityProcessorFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ServiceLoader

class EntityProcessorManager(bootContext: KrAppBootContext) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    val entityProcessors: List<EntityProcessor>

    init {
        val services = ServiceLoader.load(EntityProcessorFactory::class.java)
        logger.info("Fant ${services.toList().size} prosessorer")

        entityProcessors = with(bootContext) {
            services.map { service -> with(service) { create() } }
        }
        entityProcessors.forEach {
            logger.info("EntityProcessor: $it")
        }
    }
}
