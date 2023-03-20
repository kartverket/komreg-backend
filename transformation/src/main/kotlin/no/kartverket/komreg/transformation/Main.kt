package no.kartverket.komreg.transformation

import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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

private typealias TransformationFunction = (Reguleringsinput, Transform.NoOp) -> Transform

suspend fun executeSimpleRun(input: Reguleringsinput): Int {
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.load("reference-${getEnvironment()}.conf")
        }
    }

    // Setter opp datakildene våre
    val entitySources = EntitySourceManager(bootContext)

    val transformationRules = listOf(
        ::reguleringForKommunenr,
        ::tulleregel
    )

    val result = entitySources
        .buildEntityFlow()
        .map {
            Transform.noOp(it)
        }
        .map {
            // logger.info(it.toString())
            // Sjekke entity flowen mot reguleringsinput for å se om entityen skal transformeres
            // Lage transformeringsobjektene
            Transform.applyRules(it, input, transformationRules)
        }
        .filter { it is Transform.Transformed }
        .map { (it as Transform.Transformed).transformation }
        // Konsumere transformasjonene inn i sinken
        .onEach { logger.info(it.toString()) }

    // Entity -> Trans, Trans.NoTransform -> Trans.NoTrans -> Trans.Something -> Trans.Something

    return result.toList().size
}

sealed class Transform {
    data class NoOp(val entity: Entity) : Transform()
    data class Transformed(val transformation: Transformation) : Transform()

    companion object {
        fun noOp(entity: Entity): Transform = NoOp(entity)

        fun applyRules(transform: Transform, input: Reguleringsinput, rules: List<TransformationFunction>): Transform =
            rules.fold(transform) { acc: Transform, tf: TransformationFunction ->
                transform(acc, input, tf)
            }

        private fun transform(
            transform: Transform,
            reguleringsinput: Reguleringsinput,
            f: TransformationFunction,
        ): Transform =
            when (transform) {
                is NoOp -> f(reguleringsinput, transform)
                is Transformed -> transform
            }
    }
}

fun tulleregel(input: Reguleringsinput, transform: Transform.NoOp): Transform {
    return transform
}

fun reguleringForKommunenr(input: Reguleringsinput, transform: Transform.NoOp): Transform {
    val entity = transform.entity
    // Finn entiteter med fylkesnummer + kommuneløpenummer
    val fylkesnummer = entity.ident?.get(Fylkesnummer::class) as Fylkesnummer?
    val lopenummer = entity.ident?.get(Kommunenummer.Lopenummer::class) as Kommunenummer.Lopenummer?

    if (fylkesnummer == null || lopenummer == null) return transform

    // Finn regel i reguleringen som matcher fylkesnummer + kommuneløpenummer
    val newKommune =
        input.endringer.find { it.fra.fylkesnummer == fylkesnummer && it.fra.lopenummer == lopenummer }?.til
            ?: return transform

    // Lag en transformasjon som oppdaterer fylkesnummer og kommuneløpenummer
    logger.info("Entitet som skal transformeres: $entity")
    val ident = entity.ident ?: emptyMap<Any, Any?>()
    val newIdent = ident.plus(
        mapOf(
            Fylkesnummer::class to newKommune.fylkesnummer,
            Kommunenummer.Lopenummer::class to newKommune.lopenummer
        )
    )

    return Transform.Transformed(
        Transformation(
            id = transform.entity.id,
            transformationType = "ChangeKommune",
            transformedIdent = newIdent,
            transformedAssociatedIdents = transform.entity.associatedIdents,
            sourceObject = transform.entity.sourceObject
        )
    )
}
