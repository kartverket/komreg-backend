package no.kartverket.komreg.services

import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.featureToggle
import no.kartverket.komreg.integration.EntitySinkManager
import no.kartverket.komreg.integration.EntitySourceManager
import no.kartverket.komreg.integration.KommuneServiceManager
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import no.kartverket.komreg.logger
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.TilbakeføringsstatusRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.Storage
import no.kartverket.komreg.transformation.transform

fun transformEntities(
    input: Reguleringsinput,
    kjoringId: Int,
    transformationRepo: TransformationRepo,
    kjoringRepo: KjoringRepo,
    configRepo: TilbakeføringsstatusRepo,
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
        StorageService(transformationRepo, configRepo),
        kjoringRepo,
        configRepo,
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
    tilbakeføringsstatusRepo: TilbakeføringsstatusRepo,

) {
    val sources = EntitySourceManager(bootContext).entitySources

    val idGeneratorManager = IdGeneratorManager(bootContext)

    val skalTilbakefores = !bootContext.config.featureToggle("feature.disable_sink")

    if (tilbakeføringsstatusRepo.getTilbakeføringsstatusForKjøringId(kjoringId) == null) {
        logger.info("Førstegangskjøring av Regulering ${input.id}. Oppretter config.")
        tilbakeføringsstatusRepo.createInitialTilbakeføringsstatus(kjoringId, entitySinks.entitySinks)
    }

    val gjenværendeFørsteSinker = tilbakeføringsstatusRepo.hentIkkeStartedeTilbakeføringerForNyeEntiteter(kjoringId)

    val gjenværendeAndreSinker =
        tilbakeføringsstatusRepo.hentIkkeStartedeTilbakeføringerForErstattendeEntiteter(kjoringId)

    CoroutineScope(Dispatchers.IO).launch {
        transform(
            kjoringId,
            input,
            sources,
            emptyList(),
            entitySinks.entitySinks,
            idGeneratorManager,
            KommuneServiceManager(bootContext).kommuneService,
            storage,
            skalTilbakefores,
            gjenværendeFørsteSinker,
            gjenværendeAndreSinker,
        )

        kjoringRepo.updateKjoringEndTime(kjoringId)
        logger.info("Avsluttet alle transformasjoner!")
    }
}
