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

suspend fun executeSimpleRun(input: Reguleringsinput): Int {
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
            // logger.info(it.toString())
            // Sjekke entity flowen mot reguleringsinput for å se om entityen skal transformeres
            // Lage transformeringsobjektene
            reguleringForKommunenr(input, it)
        }
        // Konsumere transformasjonene inn i sinken
        .onEach { logger.info(it.toString()) }

    // Entity -> Trans, Trans.NoTransform -> Trans.NoTrans -> Trans.Something -> Trans.Something

    return result.toList().size
}

fun reguleringForKommunenr(input: Reguleringsinput, entity: Entity): Transformation? {
    // Finn entiteter med fylkesnummer + kommuneløpenummer
    val fylkesnummer = entity.identOf<Fylkesnummer?>()
    val lopenummer = entity.identOf<Kommunenummer.Lopenummer?>()

    if (fylkesnummer == null || lopenummer == null) return null

    // Finn regel i reguleringen som matcher fylkesnummer + kommuneløpenummer
    val newKommune =
        input.endringer.find { it.fra.fylkesnummer == fylkesnummer && it.fra.lopenummer == lopenummer }?.til

    return if (newKommune != null) {
        // Lag en transformasjon som oppdaterer fylkesnummer og kommuneløpenummer
        logger.info("Entitet som skal transformeres: $entity")
        val ident = entity.ident ?: emptyMap<Any, Any?>()
        val newIdent = ident.plus(
            Entity.typeMap(
                newKommune.fylkesnummer,
                newKommune.lopenummer
            )
        )

        Transformation(
            id = entity.id,
            transformationType = "ChangeKommune",
            transformedIdent = newIdent,
            transformedAssociatedIdents = entity.associatedIdents,
            sourceObject = entity.sourceObject
        )
    } else {
        null
    }
}
