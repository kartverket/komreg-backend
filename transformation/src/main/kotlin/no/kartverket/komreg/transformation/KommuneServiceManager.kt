package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.integration.spi.KommuneService
import no.kartverket.komreg.integration.spi.KommuneServiceFactory
import java.util.ServiceLoader
import java.util.stream.Collectors

class KommuneServiceManager(bootContext: KrAppBootContext) {
    val kommuneService: KommuneService

    init {
        val loader = ServiceLoader.load(KommuneServiceFactory::class.java)
        val providers = loader.stream().collect(Collectors.toList())
        val factory = providers.single().get()
        with(factory) {
            kommuneService = bootContext.create()
        }
    }
}
