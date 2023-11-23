package no.kartverket.komreg.integration

import no.kartverket.komreg.core.KjoringContext
import no.kartverket.komreg.integration.spi.EntitySink
import no.kartverket.komreg.integration.spi.EntitySinkFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ServiceLoader

class EntitySinkManager(bootContext: KjoringContext) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    val entitySinks: List<EntitySink>

    init {
        val services = ServiceLoader.load(EntitySinkFactory::class.java)
        logger.info("Fant ${services.toList().size} mottakere")

        entitySinks = with(bootContext) {
            services.map { service -> with(service) { create() } }
        }
        entitySinks.forEach {
            logger.info("EntitySink: ${it.id} - $it")
        }
    }
}
