package no.kartverket.komreg.transformation

import kotlinx.coroutines.flow.Flow
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.integration.spi.EntitySink
import no.kartverket.komreg.integration.spi.EntitySinkFactory
import no.kartverket.komreg.integration.spi.Transformation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ServiceLoader

class EntitySinkManager(bootContext: KrAppBootContext) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val entitySinks: List<EntitySink>

    init {
        val services = ServiceLoader.load(EntitySinkFactory::class.java)
        logger.info("Found ${services.toList().size} sink services")
        services.forEach {
            logger.info(it.toString())
        }
        entitySinks = with(bootContext) {
            services.map { service -> with(service) { create() } }
        }
        entitySinks.forEach {
            logger.info("EntitySink: ${it.id} - $it")
        }
    }

    suspend fun consume(transformations: Flow<Transformation>) {
        entitySinks.forEach {
            it.consumeTransformations(transformations)
        }
    }
}
