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
import no.kartverket.komreg.integration.LifeCycleHandlerManager
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import no.kartverket.komreg.logger
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.TilbakeføringsstatusRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.Storage
import no.kartverket.komreg.transformation.transform

@Suppress("LocalVariableName", "NonAsciiCharacters")
fun transformEntities(
    input: Reguleringsinput,
    kjoringId: Int,
    transformationRepo: TransformationRepo,
    kjoringRepo: KjoringRepo,
    tilbakeføringsstatusRepo: TilbakeføringsstatusRepo,
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
        StorageService(transformationRepo, tilbakeføringsstatusRepo, kjoringRepo),
        kjoringRepo,
        tilbakeføringsstatusRepo,
    )
}

private fun printMemoryUsage() {
    CoroutineScope(Dispatchers.Default).launch {
        val runtime = Runtime.getRuntime()
        val mb = 1024 * 1024

        while (true) {
            delay(2000)
            val used = (runtime.totalMemory() - runtime.freeMemory()) / mb
            val free = runtime.freeMemory() / mb
            val total = runtime.totalMemory() / mb
            val max = runtime.maxMemory() / mb
            logger.info("Memory. Used: $used, free: $free, total: $total, max: $max")
        }
    }
}

@Suppress("LocalVariableName", "NonAsciiCharacters")
private fun runAndWriteTransformations(
    bootContext: KrAppBootContext,
    input: Reguleringsinput,
    entitySinks: EntitySinkManager,
    kjoringId: Int,
    storage: Storage,
    kjoringRepo: KjoringRepo,
    tilbakeføringsstatusRepo: TilbakeføringsstatusRepo,

) {
    val skalTilbakefores = !bootContext.config.featureToggle("feature.disable_sink")

    val lifeCycleHandlers = LifeCycleHandlerManager(bootContext).lifeCycleHandlers
    val sources = EntitySourceManager(bootContext).entitySources
    val processors = EntityProcessorManager(bootContext).entityProcessors

    val idGeneratorManager = IdGeneratorManager(bootContext)

    val mdc = CoroutineMDC(
        mapOf(
            "kjoringId" to kjoringId.toString(),
        ),
    )

    CoroutineScope(Dispatchers.IO + mdc).launch {
        logger.info(FAG, "Startet å kjøre transformasjoner")

        if (tilbakeføringsstatusRepo.getTilbakeføringsstatusForKjøringId(kjoringId) == null) {
            logger.info("Førstegangskjøring av Regulering ${input.id}. Oppretter tilbakeføringsstatus for sinker.")
            tilbakeføringsstatusRepo.createTilbakeføringsstatusForKjoring(kjoringId, entitySinks.entitySinks)
        }

        CoroutineScope(Dispatchers.IO).launch {
            transform(
                kjoringId,
                input,
                lifeCycleHandlers,
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
}
