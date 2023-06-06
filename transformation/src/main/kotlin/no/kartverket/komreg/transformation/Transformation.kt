package no.kartverket.komreg.transformation

import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Transformation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger(object {}::class.java)

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

suspend fun transformEntities(input: Reguleringsinput) {
    val transformStatus = TransformationStatusForRegulering().also { transformStatuses[input.id] = it }
    transformStatus.start()
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.invalidateCaches()
            ConfigFactory.load("properties.conf")
        }
    }

    val entitySinks = EntitySinkManager(bootContext)

    if (input.fylker.isNotEmpty()) {
        writeFylker(bootContext, input, entitySinks)
        return
    }

    printMemoryUsage()

    runAndWriteTransformations(bootContext, transformStatus, input, entitySinks)
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
                    transformationType = "NyttFylke",
                    transformedIdent = Ident(fylke.fylkesnummer),
                    transformedAssociatedIdents = null,
                    resultObject = Fylkesdata(fylke.fylkesnavn.name),
                ),
            )
        }
    }

    logger.info("Starter tilbakeføring av fylker")
    entitySinks.consume(transformedFylker, input.ikrafttredelsesdato)
}

private fun runAndWriteTransformations(
    bootContext: KrAppBootContext,
    transformStatus: TransformationStatusForRegulering,
    input: Reguleringsinput,
    entitySinks: EntitySinkManager,
) {
    val sources = EntitySourceManager(bootContext).entitySources

    CoroutineScope(Dispatchers.IO).launch {
        sources.map {
            val flow = it.entityFlow
            val type = it.id
            val statusForSource = TransformationStatusForSource(source = type)
            transformStatus.addSourceStatus(statusForSource)
            val transformResult = flow
                .onStart {
                    logger.info("Starting flow of type: $type")
                    statusForSource.firstTransformation = Clock.System.now()
                }
                .mapNotNull { entity -> transformerKommunenummer(input, entity) }
                .onEach {
                    statusForSource.numberOfTransformations += 1
                }
                .onCompletion {
                    logger.info("Completed transformations for flow of type $type")
                    statusForSource.transformationFinished = Clock.System.now()
                }
            // .launchIn(CoroutineScope(Dispatchers.IO))
            val newFlow: Flow<Transformation> =
                transformResult.toList().asFlow().onStart {
                    logger.info("Starting second part of flow")
                }
            logger.info("Starter tilbakeføring fra source: $type")
            entitySinks.consume(newFlow, input.ikrafttredelsesdato)
            logger.info("Fullført tilbakeføring av source: $type")
            statusForSource.tilbakeføringFinished = Clock.System.now()
        }
        transformStatus.finish()
    }
}