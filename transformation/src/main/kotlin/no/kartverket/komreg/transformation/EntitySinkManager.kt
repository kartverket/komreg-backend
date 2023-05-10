package no.kartverket.komreg.transformation

import kotlinx.coroutines.flow.Flow
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.integration.spi.EntitySink
import no.kartverket.komreg.integration.spi.EntitySinkFactory
import no.kartverket.komreg.integration.spi.Transformation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ServiceLoader
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class EntitySinkManager(private val bootContext: KrAppBootContext) {

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

    @OptIn(ExperimentalTime::class)
    suspend fun consume(transformations: Flow<Transformation>) {
        bootContext.config.featureToggle(
            "feature.disable_sink",
            enabled = {
                entitySinks.forEach { sink ->
                    transformations.collect {
                        logger.info("Lokal stub ${sink.id} konsumerer transformasjon: $it")
                    }
                }
            },
            disabled = {
                entitySinks.forEach {
                    val (_, time) = measureTimedValue {
                        it.consumeTransformations(transformations)
                    }
                    logger.info("Sink ${it.id} took ${time.inWholeMilliseconds}ms")
                }
            },
        )
    }
}
