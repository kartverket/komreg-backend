package no.kartverket.komreg.integration

import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.integration.spi.LifeCycleHandler
import no.kartverket.komreg.integration.spi.LifeCycleHandlerFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class LifeCycleHandlerManager(bootContext: KrAppBootContext) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    val lifeCycleHandlers: List<LifeCycleHandler>

    init {
        val services = ServiceLoader.load(LifeCycleHandlerFactory::class.java)
        logger.info("Fant ${services.toList().size} LifeCycleHandlers")

        lifeCycleHandlers = with(bootContext) {
            services.map { service -> with(service) { create() } }
        }
        lifeCycleHandlers.forEach {
            logger.info("LifeCycleHandler: $it")
        }
    }
}
