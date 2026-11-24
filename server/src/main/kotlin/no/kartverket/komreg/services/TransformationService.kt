package no.kartverket.komreg.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.kartverket.komreg.core.KjoringContext
import no.kartverket.komreg.core.logging.CoroutineMDC
import no.kartverket.komreg.core.logging.FAG
import no.kartverket.komreg.env
import no.kartverket.komreg.integration.EntityProcessorManager
import no.kartverket.komreg.integration.EntitySinkManager
import no.kartverket.komreg.integration.EntitySourceManager
import no.kartverket.komreg.integration.IdTypeProviderManager
import no.kartverket.komreg.integration.LifeCycleHandlerManager
import no.kartverket.komreg.logger
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.TilbakeføringsstatusRepo
import no.kartverket.komreg.repositories.TransformationRepo
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.Storage
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.transform
import org.slf4j.MDC
import java.lang.management.ManagementFactory

/** Reglene en kjøring bruker, én variant per [no.kartverket.komreg.Transformasjonsmotor]. */
sealed interface Kjoringsregler {
    val reguleringId: String
    val ikrafttredelsesdato: LocalDate

    /** `legacy`: `IdentTransformer` med regler fra `Regulering`-DTO-en. */
    data class Legacy(val input: Reguleringsinput) : Kjoringsregler {
        override val reguleringId: String get() = input.id
        override val ikrafttredelsesdato: LocalDate get() = input.ikrafttredelsesdato
    }

    /** `reform`: `ReformTransformer` med lagrede, validerte `ReformOp`-er. */
    data class Reform(
        override val reguleringId: String,
        val claims: ReformClaims,
        override val ikrafttredelsesdato: LocalDate,
    ) : Kjoringsregler
}

@Suppress("LocalVariableName", "NonAsciiCharacters")
fun transformEntities(
    regler: Kjoringsregler,
    kjoringContext: KjoringContext,
    transformationRepo: TransformationRepo,
    kjoringRepo: KjoringRepo,
    tilbakeføringsstatusRepo: TilbakeføringsstatusRepo,
    erForsteGangkjoring: Boolean,
) {
    logger.info("Starter transformasjon!")

    val entitySinks = EntitySinkManager(kjoringContext)

    runAndWriteTransformations(
        kjoringContext,
        regler,
        entitySinks,
        StorageService(transformationRepo, tilbakeføringsstatusRepo, kjoringRepo),
        kjoringRepo,
        tilbakeføringsstatusRepo,
        erForsteGangkjoring,
    )
}

@Suppress("LocalVariableName", "NonAsciiCharacters")
private fun runAndWriteTransformations(
    kjoringContext: KjoringContext,
    regler: Kjoringsregler,
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
    val idTypeProviders = IdTypeProviderManager()

    CoroutineScope(Dispatchers.IO + CoroutineMDC()).launch {
        MDC.put("kjoringId", kjoringId.toString())
        logger.info(FAG, "Startet å kjøre transformasjoner")

        if (tilbakeføringsstatusRepo.getTilbakeføringsstatusForKjøringId(kjoringId) == null) {
            logger.info("Førstegangskjøring av Regulering ${regler.reguleringId}. Oppretter tilbakeføringsstatus for sinker.")
            tilbakeføringsstatusRepo.createTilbakeføringsstatusForKjoring(kjoringId, entitySinks.entitySinks)
        }

        val transformJob = launch(Dispatchers.IO) {
            when (regler) {
                is Kjoringsregler.Legacy -> transform(
                    kjoringId,
                    regler.input,
                    lifeCycleHandlers,
                    sources,
                    processors,
                    entitySinks.entitySinks,
                    kjoringContext.idGenerators,
                    storage,
                    skalTilbakefores,
                    erFortegangskjoring,
                )

                is Kjoringsregler.Reform -> transform(
                    kjoringId,
                    regler.claims,
                    regler.ikrafttredelsesdato,
                    idTypeProviders::idTypeFor,
                    lifeCycleHandlers,
                    sources,
                    processors,
                    entitySinks.entitySinks,
                    kjoringContext.idGenerators,
                    storage,
                    skalTilbakefores,
                    erFortegangskjoring,
                )
            }

            kjoringRepo.updateKjoringEndTime(kjoringId)
            logger.info(FAG, "Avsluttet alle transformasjoner!")
        }

        transformJob.invokeOnCompletion { cause ->
            val resultat = when (cause) {
                null -> "kjørt ferdig"
                is CancellationException -> "avbrutt"
                else -> "feilet: ${cause.message}"
            }
            logger.info("Transformasjoner $resultat")
            val memoryMXBean = ManagementFactory.getMemoryMXBean()
            val heap = memoryMXBean.heapMemoryUsage
            logger.info(
                "Heap før GC: used={} MiB committed={} MiB",
                heap.used / 1024 / 1024,
                heap.committed / 1024 / 1024
            )
            System.gc()
            val heapAfter = memoryMXBean.heapMemoryUsage
            logger.info(
                "Heap etter GC-request: used={} MiB committed={} MiB",
                heapAfter.used / 1024 / 1024,
                heapAfter.committed / 1024 / 1024
            )
        }
    }
}
