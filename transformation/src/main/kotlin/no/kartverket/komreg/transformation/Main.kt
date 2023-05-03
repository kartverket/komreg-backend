package no.kartverket.komreg.transformation

import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.KrAppBootContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger(object {}::class.java)

fun getEnvironment(): String = System.getenv("environment") ?: "local"

@Serializable
data class TransformationInfo(
    var numberOfTransformations: Int = 0,
    var firstTransformation: Instant? = null,
    var lastTransformation: Instant? = null,
)

@Serializable
data class TransformStatus(
    var numberOfTransformationsByType: MutableMap<String, TransformationInfo>? = mutableMapOf(),
    var started: Instant? = null,
    var finished: Instant? = null,
) {
    fun start() {
        started = Clock.System.now()
        finished = null
    }

    fun finish() {
        finished = Clock.System.now()
    }
}

val transformStatuses = mutableMapOf<String, TransformStatus>()

suspend fun transformEntities(input: Reguleringsinput) {
    val transformStatus = TransformStatus().also { transformStatuses[input.id] = it }
    transformStatus.start()
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.load("reference-${getEnvironment()}.conf")
        }
    }

    val entitySources = EntitySourceManager(bootContext)
    val entitySinks = EntitySinkManager(bootContext)

    CoroutineScope(Dispatchers.Default).launch {
        val runtime = Runtime.getRuntime()
        val mb = 1024 * 1024

        while (true) {
            delay(5000)
            val used = (runtime.totalMemory() - runtime.freeMemory()) / mb
            val free = runtime.freeMemory() / mb
            val total = runtime.totalMemory() / mb
            val max = runtime.maxMemory() / mb
            logger.info("Memory. Used: $used, free: $free, total: $total, max: $max")
        }
    }

    CoroutineScope(Dispatchers.IO).launch {
        val result = entitySources
            .buildEntityFlow()
            .mapNotNull { transformerKommunenummer(input, it) }
            .onEach { logger.info("Transformert entitet: $it") }
            .onEach {
                transformStatus
                    .numberOfTransformationsByType
                    ?.merge(
                        it.id.type.toString(),
                        TransformationInfo(
                            1,
                            Clock.System.now(),
                            Clock.System.now(),
                        ),
                    ) { old, new ->
                        TransformationInfo(
                            old.numberOfTransformations + 1,
                            old.firstTransformation ?: new.firstTransformation,
                            new.lastTransformation,
                        )
                    }
            }

        logger.info("Starter tilbakeføring..")
        entitySinks.consume(result)
        logger.info("Fullført tilbakeføring!")
        transformStatus.finish()
    }
}
