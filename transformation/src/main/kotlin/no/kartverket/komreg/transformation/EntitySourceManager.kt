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
        entitySources.forEach {
            logger.info("EntitySource: ${it.id} - $it")
        }

        isLocal = System.getenv("environment") == "local" || System.getenv("environment") == null
        logger.info("Current environment: ${System.getenv("environment")}")

        // If running in local environment, flip the order of the sources, then flip the order of veg and vegadresse
        if (isLocal) {
            entitySources = createEntitySourcesForLocalEnv(bootContext)
        }
    }

    private fun createEntitySourcesForLocalEnv(
        bootContext: KrAppBootContext,
    ): List<EntitySource> {
        logger.info("Converting custom source order for local env")
        logger.info("Flipping whole source list")
        val sources = EntitySourceManager(bootContext).entitySources.reversed().toMutableList()
        var vegIndex = -1
        var vegadresseIndex = -1

        for (i in sources.indices) {
            val currentId = sources[i].id.lowercase()

            if (currentId.contains("veg") && !currentId.contains("vegadresse") && vegIndex == -1) {
                vegIndex = i
            }
            if (currentId.contains("vegadresse") && vegadresseIndex == -1) {
                vegadresseIndex = i
            }
        }

        if (vegIndex != -1 && vegadresseIndex != -1) {
            logger.info("Swapping entities containing 'veg' and 'vegadresse' in source list")
            val temp = sources[vegIndex]
            sources[vegIndex] = sources[vegadresseIndex]
            sources[vegadresseIndex] = temp
        }
        return sources
    }
}
