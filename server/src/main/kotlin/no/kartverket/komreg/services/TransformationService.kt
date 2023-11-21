package no.kartverket.komreg.services

import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.logging.CoroutineMDC
import no.kartverket.komreg.core.logging.FAG
import no.kartverket.komreg.featureToggle
import no.kartverket.komreg.integration.EntityProcessorManager
import no.kartverket.komreg.integration.EntitySinkManager
import no.kartverket.komreg.integration.EntitySourceManager
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import no.kartverket.komreg.logger
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.Storage
import no.kartverket.komreg.transformation.transform

fun transformEntities(
    input: Reguleringsinput,
    kjoringId: Int,
    transformationRepo: TransformationRepo,
    kjoringRepo: KjoringRepo,
) {
    logger.info("Starter transformasjon!")

    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.invalidateCaches()
            ConfigFactory.load("properties.conf")
        }
    }

    val entitySinks = EntitySinkManager(bootContext)

    printMemoryUsage()

    runAndWriteTransformations(
        bootContext,
        input,
        entitySinks,
        kjoringId,
        StorageService(transformationRepo),
        kjoringRepo,
    )
}

private fun printMemoryUsage() {
    CoroutineScope(Dispatchers.Default).launch {
        val runtime = Runtime.getRuntime()
        val mb = 1024 * 1024

        while (true) {
            delay(30000)
            val used = (runtime.totalMemory() - runtime.freeMemory()) / mb
            val free = runtime.freeMemory() / mb
            val total = runtime.totalMemory() / mb
            val max = runtime.maxMemory() / mb
            logger.info("Memory. Used: $used, free: $free, total: $total, max: $max")
        }
    }
}

private fun runAndWriteTransformations(
    bootContext: KrAppBootContext,
    input: Reguleringsinput,
    entitySinks: EntitySinkManager,
    kjoringId: Int,
    storage: Storage,
    kjoringRepo: KjoringRepo,

) {
    val sources = EntitySourceManager(bootContext).entitySources
    val processors = EntityProcessorManager(bootContext).entityProcessors

    val idGeneratorManager = IdGeneratorManager(bootContext)

    val skalTilbakefores = !bootContext.config.featureToggle("feature.disable_sink")

    val mdc = CoroutineMDC(mapOf(
        "kjoringId" to kjoringId.toString()
    ))
    CoroutineScope(Dispatchers.IO + mdc).launch {
        logger.info(FAG, "Startet å kjøre transformasjoner")
        transform(
            kjoringId,
            input,
            sources,
            processors,
            entitySinks.entitySinks,
            idGeneratorManager,
            storage,
            skalTilbakefores,
        )

        kjoringRepo.updateKjoringEndTime(kjoringId)
        logger.info(FAG, "Avsluttet alle transformasjoner!")
    }
}
