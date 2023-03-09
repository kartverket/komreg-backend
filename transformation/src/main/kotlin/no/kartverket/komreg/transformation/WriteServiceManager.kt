package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.OldToNewKommune
import no.kartverket.komreg.integration.spi.WriteService
import no.kartverket.komreg.integration.spi.WriteServiceFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ServiceLoader

class WriteServiceManager(bootContext: KrAppBootContext) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val writeSources: List<WriteService<*>>

    init {
        val services = ServiceLoader.load(WriteServiceFactory::class.java)
        logger.info("Found ${services.toList().size} services")
        services.forEach {
            logger.info(it.toString())
        }
        writeSources = with(bootContext) {
            services.map { service -> with(service) { create() } }
        }
    }

    fun getKommuneWriteService(): WriteService<OldToNewKommune> {
        return writeSources[0]!! as WriteService<OldToNewKommune>
    }
}
