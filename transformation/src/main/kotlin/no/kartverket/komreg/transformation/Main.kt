package no.kartverket.komreg.transformation

import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Transformation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger(object {}::class.java)

fun getEnvironment(): String = System.getenv("environment") ?: "local"

suspend fun transformEntities(input: Reguleringsinput) {
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.load("reference-${getEnvironment()}.conf")
        }
    }

    val entitySources = EntitySourceManager(bootContext)
    val entitySinks = EntitySinkManager(bootContext)

    CoroutineScope(Dispatchers.IO).launch {
        val result = entitySources
            .buildEntityFlow()
            .mapNotNull { transformerKommunenummer(input, it) }
            .onEach { logger.info("Transformert entitet: $it") }

        logger.info("Starter tilbakeføring..")
        entitySinks.consume(result)
        logger.info("Fullført tilbakeføring!")
    }
}

fun transformerKommunenummer(input: Reguleringsinput, entity: Entity): Transformation? {
    // Finn entiteter med fylkesnummer + kommuneløpenummer
    val fylkesnummer = entity.ident?.get<Fylkesnummer?>()
    val lopenummer = entity.ident?.get<Kommunenummer.Lopenummer?>()

    if (fylkesnummer == null || lopenummer == null) return null

    // Finn regel i reguleringen som matcher fylkesnummer + kommuneløpenummer
    val endring =
        input.endringer.find {
            it.fra.fylkesnummer == fylkesnummer && it.fra.lopenummer == lopenummer
        }?.til

    return if (endring != null) {
        // Lag en transformasjon som oppdaterer fylkesnummer og kommuneløpenummer
        val newIdent = entity.ident?.transform(
            Ident(
                endring.fylkesnummer,
                endring.lopenummer,
            ),
        )

        Transformation(
            id = entity.id,
            transformationType = "ChangeKommunenummer",
            sourceEntity = entity,
            transformedIdent = newIdent,
            transformedAssociatedIdents = entity.associatedIdents,
        )
    } else {
        null
    }
}
