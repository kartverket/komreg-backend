package no.kartverket.komreg.transformation

import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Transformation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger(object {}::class.java)

fun getEnvironment(): String = System.getenv("environment") ?: "local"

suspend fun transformEntities(input: Reguleringsinput): List<Transformation> {
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.load("reference-${getEnvironment()}.conf")
        }
    }

    // Setter opp datakildene våre
    val entitySources = EntitySourceManager(bootContext)

    val result = entitySources
        .buildEntityFlow()
        .mapNotNull {
            // Sjekke entity flowen mot reguleringsinput, og lage transformeringsobjektene
            transformerKommunenummer(input, it)
        }
        // Konsumere transformasjonene inn i sinken
        .onEach { logger.info("Transformert entitet: $it") }

    return result.toList()
}

fun transformerKommunenummer(input: Reguleringsinput, entity: Entity): Transformation? {
    // Finn entiteter med fylkesnummer + kommuneløpenummer
    val fylkesnummer = entity.identOf<Fylkesnummer?>()
    val lopenummer = entity.identOf<Kommunenummer.Lopenummer?>()

    if (fylkesnummer == null || lopenummer == null) return null

    // Finn regel i reguleringen som matcher fylkesnummer + kommuneløpenummer
    val endring =
        input.endringer.find {
            it.fra.fylkesnummer == fylkesnummer && it.fra.lopenummer == lopenummer
        }?.til

    return if (endring != null) {
        // Lag en transformasjon som oppdaterer fylkesnummer og kommuneløpenummer
        logger.info("Entitet som skal transformeres: $entity")
        val ident = entity.ident ?: emptyMap<Any, Any?>()
        val newIdent = ident.plus(
            Entity.typeMap(
                endring.fylkesnummer,
                endring.lopenummer,
            ),
        )

        Transformation(
            id = entity.id,
            transformationType = "ChangeKommunenummer",
            transformedIdent = newIdent,
            transformedAssociatedIdents = entity.associatedIdents,
            sourceObject = entity, // entity.sourceObject?
        )
    } else {
        null
    }
}
