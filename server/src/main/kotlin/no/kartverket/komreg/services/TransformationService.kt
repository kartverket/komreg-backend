package no.kartverket.komreg.services

import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaLocalDate
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.integration.EntitySinkManager
import no.kartverket.komreg.integration.EntitySourceManager
import no.kartverket.komreg.integration.KommuneServiceManager
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.logger
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.Storage
import no.kartverket.komreg.transformation.transform

@Serializable
data class TransformationStatusForSource(
    val source: String?,
    var numberOfTransformations: Int = 0,
    var firstTransformation: Instant? = null,
    var transformationFinished: Instant? = null,
    var tilbakeføringFinished: Instant? = null,
)

@Serializable
data class TransformationStatusForRegulering(
    var transformationsBySource: MutableList<TransformationStatusForSource> = mutableListOf(),
    var started: Instant? = null,
    var finished: Instant? = null,
) {

    fun addSourceStatus(sourceStatus: TransformationStatusForSource) {
        transformationsBySource.add(sourceStatus)
    }

    fun start() {
        started = Clock.System.now()
        finished = null
    }

    fun finish() {
        finished = Clock.System.now()
    }
}

val transformStatuses = mutableMapOf<String, TransformationStatusForRegulering>()

fun transformEntities(
    input: Reguleringsinput,
    kjoringId: Int,
    transformationRepo: TransformationRepo,
    kjoringRepo: KjoringRepo,
) {
    logger.info("Starter transformasjon!")
    val transformStatus = TransformationStatusForRegulering().also { transformStatuses[input.id] = it }
    transformStatus.start()
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
        transformStatus,
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

private suspend fun writeFylker(
    bootContext: KrAppBootContext,
    input: Reguleringsinput,
    entitySinks: EntitySinkManager,
) {
    val kommuneService = KommuneServiceManager(bootContext).kommuneService

    logger.info("Følgende fylker skal opprettes:")
    input.fylker.forEach { fylke ->
        logger.info("Fylke: ${fylke.fylkesnummer} ${fylke.fylkesnavn}")
    }

    val transformedFylker = flow {
        input.fylker.forEach { fylke ->
            emit(
                Transformation(
                    id = kommuneService.idForFylke(fylke.fylkesnummer),
                    sourceEntity = null,
                    transformedIdent = Ident(fylke.fylkesnummer),
                    resultObject = Fylkesdata(fylke.fylkesnavn.name.uppercase()),
                ),
            )
        }
    }

    logger.info("Starter tilbakeføring av fylker")
    entitySinks.consume(transformedFylker, input.ikrafttredelsesdato.toJavaLocalDate())
    logger.info("Fullført tilbakeføring av fylker")
}

private fun runAndWriteTransformations(
    bootContext: KrAppBootContext,
    transformStatus: TransformationStatusForRegulering,
    input: Reguleringsinput,
    entitySinks: EntitySinkManager,
    kjoringId: Int,
    storage: Storage,
    kjoringRepo: KjoringRepo,

) {
    val sources = EntitySourceManager(bootContext).entitySources

    val idGeneratorManager = IdGeneratorManager(bootContext)

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
        )

        transformStatus.finish()
        kjoringRepo.updateKjoringEndTime(kjoringId)
        logger.info("Avsluttet alle transformasjoner!")
    }
}
