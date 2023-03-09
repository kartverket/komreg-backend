package no.kartverket.komreg.core.impl

import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.scan
import no.kartverket.komreg.core.KrAppBootContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger(object{}::class.java)

suspend fun main() {
    val bootContext = object : KrAppBootContext {
        val fallback = ConfigFactory.empty()
        override val config by lazy {
            ConfigFactory.load("reference-dev.conf")
        }
    }
    val enititySources = EntitySourceManager(bootContext)

    val z = enititySources
        .buildEntityFlow()
        .scan(1) { accumulator, value ->
            logger.info(value.toString())
            if (accumulator and 10240 == 0) {
                logger.info("Lastet ned $accumulator")
            }
            accumulator + 1
        }.lastOrNull()

    logger.info("Fant masse gøy: $z")
}
