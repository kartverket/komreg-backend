package no.kartverket.komreg.transformation

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.EntitySource
import no.kartverket.komreg.integration.spi.EntitySourceFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ServiceLoader

class EntitySourceManager(bootContext: KrAppBootContext) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val entitySources: List<EntitySource>

    init {
        val services = ServiceLoader.load(EntitySourceFactory::class.java)
        logger.info("Found ${services.toList().size} services")
        services.forEach {
            logger.info(it.toString())
        }
        entitySources = with(bootContext) {
            services.map { service -> with(service) { create() } }
        }
        entitySources.forEach {
            logger.info("EntitySource: ${it.id} - $it")
        }
    }

    @OptIn(FlowPreview::class)
    fun buildEntityFlow(): Flow<Entity> = entitySources
        .asFlow()
        .flatMapMerge { it.entityFlow }
}
