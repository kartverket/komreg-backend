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
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.integration.spi.EntitySource
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Transformation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger(object {}::class.java)

private var isLocal = false

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

fun transformEntities(input: Reguleringsinput) {
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

// Flips the whole sources list, then flips order of just veg and vegadresse
private fun createSourceListWithCustomOrderForLocalEnv(
    bootContext: KrAppBootContext,
): MutableList<EntitySource> {
    logger.info("Converting custom source order for local env")
    logger.info("Flipping whole source list")
    val sources = EntitySourceManager(bootContext).entitySources.reversed().toMutableList()
    var vegIndex = -1
    var vegadresseIndex = -1

    for (i in sources.indices) {
        val currentId = sources[i].id.lowercase()

        if (currentId.contains("veg") && !currentId.contains("vegadresse") && vegIndex == -1) {
            vegIndex = i
        }
        if (currentId.contains("vegadresse") && vegadresseIndex == -1) {
            vegadresseIndex = i
        }
    }

    if (vegIndex != -1 && vegadresseIndex != -1) {
        logger.info("Swapping entities containing 'veg' and 'vegadresse' in source list")
        val temp = sources[vegIndex]
        sources[vegIndex] = sources[vegadresseIndex]
        sources[vegadresseIndex] = temp
    }
    return sources
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
                    resultObject = Fylkesdata(fylke.fylkesnavn.name),
                ),
            )
        }
    }

    logger.info("Starter tilbakeføring av fylker")
    entitySinks.consume(transformedFylker, input.ikrafttredelsesdato)
    logger.info("Fullført tilbakeføring av fylker")
}

private suspend fun writeKommuner(
    bootContext: KrAppBootContext,
    input: Reguleringsinput,
    entitySinks: EntitySinkManager,
) {
    val kommuneService = KommuneServiceManager(bootContext).kommuneService

    logger.info("Følgende kommuner skal opprettes:")
    input.kommuner.forEach { kommune ->
        logger.info("Kommune: ${kommune.kommunenummer} ${kommune.kommunenavn}")
    }

    val transformedKommuner = flow {
        input.kommuner.forEach { kommune ->
            emit(
                Transformation(
                    id = kommuneService.idForKommune(kommune.kommunenummer),
                    sourceEntity = null,
                    transformationType = "NyKommune",
                    transformedIdent = Ident(kommune.kommunenummer.fylkesnummer, kommune.kommunenummer.lopenummer),
                    resultObject = Kommunedata(kommune.kommunenavn.name),
                ),
            )
        }
    }

    logger.info("Starter tilbakeføring av kommuner")
    entitySinks.consume(transformedKommuner, input.ikrafttredelsesdato)
    logger.info("Fullført tilbakeføring av kommuner")
}

private fun runAndWriteTransformations(
    bootContext: KrAppBootContext,
    transformStatus: TransformationStatusForRegulering,
    input: Reguleringsinput,
    entitySinks: EntitySinkManager,
) {
    isLocal = System.getenv("environment") == "local" || System.getenv("environment") == null
    logger.info("Current environment: ${System.getenv("environment")}")

    var sources: MutableList<EntitySource>

    if (isLocal) {
        sources = createSourceListWithCustomOrderForLocalEnv(bootContext)
    } else {
        sources = EntitySourceManager(bootContext).entitySources.toMutableList()
    }

    CoroutineScope(Dispatchers.IO).launch {
        if (input.fylker.isNotEmpty()) {
            writeFylker(bootContext, input, entitySinks)
        }

        if (input.kommuner.isNotEmpty()) {
            writeKommuner(bootContext, input, entitySinks)
        }

        sources.map {
            val flow = it.entityFlow
            val type = it.id
            val statusForSource = TransformationStatusForSource(source = type)
            transformStatus.addSourceStatus(statusForSource)
            val transformResult = flow
                .onStart {
                    logger.info("Starter flow av type: $type")
                    statusForSource.firstTransformation = Clock.System.now()
                }
                .mapNotNull { entity -> transformerKommunenummer(input, entity) }
                .onEach {
                    statusForSource.numberOfTransformations += 1
                }
                .onCompletion {
                    logger.info("Fullført transformasjoner for flow av type $type")
                    statusForSource.transformationFinished = Clock.System.now()
                }

            val newFlow: Flow<Transformation> = transformResult.toList().asFlow()

            logger.info("Starter tilbakeføring fra source: $type")
            entitySinks.consume(newFlow, input.ikrafttredelsesdato)
            logger.info("Fullført tilbakeføring av source: $type")
            statusForSource.tilbakeføringFinished = Clock.System.now()
        }
        transformStatus.finish()
        logger.info("Avsluttet transformasjon!")
    }
}
