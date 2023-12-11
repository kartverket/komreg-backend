package no.kartverket.komreg.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.kartverket.komreg.core.KjoringContext
import no.kartverket.komreg.core.logging.CoroutineMDC
import no.kartverket.komreg.core.logging.FAG
import no.kartverket.komreg.env
import no.kartverket.komreg.integration.EntityProcessorManager
import no.kartverket.komreg.integration.EntitySinkManager
import no.kartverket.komreg.integration.EntitySourceManager
import no.kartverket.komreg.integration.LifeCycleHandlerManager
import no.kartverket.komreg.logger
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.TilbakeføringsstatusRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.Storage
import no.kartverket.komreg.transformation.transform
import org.slf4j.MDC

@Suppress("LocalVariableName", "NonAsciiCharacters")
fun transformEntities(
    input: Reguleringsinput,
    kjoringContext: KjoringContext,
    transformationRepo: TransformationRepo,
    kjoringRepo: KjoringRepo,
    tilbakeføringsstatusRepo: TilbakeføringsstatusRepo,
    erForsteGangkjoring: Boolean,
) {
    logger.info("Starter transformasjon!")

    val entitySinks = EntitySinkManager(kjoringContext)

    printMemoryUsage()

    runAndWriteTransformations(
        kjoringContext,
        input,
        entitySinks,
        StorageService(transformationRepo, tilbakeføringsstatusRepo, kjoringRepo),
        kjoringRepo,
        tilbakeføringsstatusRepo,
        erForsteGangkjoring,
    )
}

private fun printMemoryUsage() {
    CoroutineScope(Dispatchers.Default).launch {
        val runtime = Runtime.getRuntime()
        val mb = 1024 * 1024

        while (true) {
            delay(30_000)
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
    kjoringContext: KjoringContext,
    input: Reguleringsinput,
    entitySinks: EntitySinkManager,
    storage: Storage,
    kjoringRepo: KjoringRepo,
    tilbakeføringsstatusRepo: TilbakeføringsstatusRepo,
    erFortegangskjoring: Boolean,

) {
    val kjoringId = kjoringContext.kjoringId

    // true hvis TOGGLE_SINK_OFF er false eller ikke finnes
    val skalTilbakefores = !(env["TOGGLE_SINK_OFF"]?.toBoolean() ?: false)

    val lifeCycleHandlers = LifeCycleHandlerManager(kjoringContext).lifeCycleHandlers
    val sources = EntitySourceManager(kjoringContext).entitySources
    val processors = EntityProcessorManager(kjoringContext).entityProcessors

    CoroutineScope(Dispatchers.IO + CoroutineMDC()).launch {
        MDC.put("kjoringId", kjoringId.toString())
        logger.info(FAG, "Startet å kjøre transformasjoner")

        if (tilbakeføringsstatusRepo.getTilbakeføringsstatusForKjøringId(kjoringId) == null) {
            logger.info("Førstegangskjøring av Regulering ${input.id}. Oppretter tilbakeføringsstatus for sinker.")
            tilbakeføringsstatusRepo.createTilbakeføringsstatusForKjoring(kjoringId, entitySinks.entitySinks)
        }

        launch(Dispatchers.IO) {
            transform(
                kjoringId,
                input,
                lifeCycleHandlers,
                sources,
                processors,
                entitySinks.entitySinks,
                kjoringContext.idGenerators,
                storage,
                skalTilbakefores,
                erFortegangskjoring,
            )

            kjoringRepo.updateKjoringEndTime(kjoringId)
            logger.info(FAG, "Avsluttet alle transformasjoner!")
        }
    }
}
