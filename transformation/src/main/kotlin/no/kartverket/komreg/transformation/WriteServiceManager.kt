package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.OldToNewKommune
import no.kartverket.komreg.integration.spi.WriteService
import no.kartverket.komreg.integration.spi.WriteServiceFactory
import java.util.ServiceLoader

class WriteServiceManager(bootContext: KrAppBootContext) {
    private val writeSources: List<WriteService<*>>

    init {
        val services = ServiceLoader.load(WriteServiceFactory::class.java)
        println("Found ${services.toList().size} services")
        services.forEach {
            println(it.toString())
        }
        writeSources = with(bootContext) {
            services.map { service -> with(service) { create() } }
        }
    }

    fun getKommuneWriteService(): WriteService<OldToNewKommune> {
        return writeSources[0]!! as WriteService<OldToNewKommune>
    }
}
