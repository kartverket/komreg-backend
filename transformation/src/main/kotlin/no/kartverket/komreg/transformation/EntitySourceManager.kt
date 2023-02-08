package no.kartverket.komreg.transformation

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.data.RawData
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.SimpleEntitySource
import no.kartverket.komreg.integration.spi.SimpleEntitySourceFactory
import java.util.ServiceLoader

class EntitySourceManager(bootContext: KrAppBootContext) {
    private val entitySources: List<SimpleEntitySource<*>>

    init {
        val services = ServiceLoader.load(SimpleEntitySourceFactory::class.java)
        println("Found ${services.toList().size} services")
        services.forEach {
            println(it.toString())
        }
        entitySources = with(bootContext) {
            services.map { service -> with(service) { create() } }
        }
    }

    @OptIn(FlowPreview::class)
    fun buildEntityFlow(): Flow<RawData<*>> = entitySources
        .asFlow()
        .flatMapMerge { it.entityFlow }

    fun buildMatrikkelnummerFlow(): Flow<RawData<Matrikkelnummer>> =
        entitySources
            .asFlow()
            .flatMapMerge { it.entityFlow }
            .filter { it.data is Matrikkelnummer } as Flow<RawData<Matrikkelnummer>>
}
