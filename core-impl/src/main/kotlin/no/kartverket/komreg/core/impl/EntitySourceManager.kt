@file:OptIn(FlowPreview::class)

package no.kartverket.komreg.core.impl

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.Product
import no.kartverket.komreg.integration.spi.EntityContext
import no.kartverket.komreg.integration.spi.EntityContext.Companion.toEntityContext
import no.kartverket.komreg.integration.spi.EntitySource
import no.kartverket.komreg.integration.spi.EntitySourceFactory
import java.util.ServiceLoader

class EntitySourceManager(bootContext: KrAppBootContext) {
    private val entitySources: List<EntitySource<*>>

    init {
        with(bootContext) {
            entitySources = ServiceLoader
                .load(EntitySourceFactory::class.java)
                .map {
                    with(it) {
                        create()
                    }
                }
        }
    }

    fun buildEntityFlow(): Flow<EntityContext<Product<*>>> = entitySources
        .asFlow()
        .flatMapMerge { it.entityFlow }
        .map { it.toEntityContext() }

}