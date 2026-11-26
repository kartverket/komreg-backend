package no.kartverket.komreg.services

import arrow.core.getOrElse
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.verdi
import no.kartverket.komreg.env
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Payload
import no.kartverket.komreg.logger
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.routes.LegacyRegulering
import no.kartverket.komreg.routes.ReformOpRegulering
import no.kartverket.komreg.routes.toReguleringsinput
import no.kartverket.komreg.transformation.Fylkeendring
import no.kartverket.komreg.transformation.Kommuneendring
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.ReformOp
import no.statkart.matrikkel.komreg.stedsnavn.ssrJson
import java.io.Writer
import kotlin.reflect.KClass

class StedsnavnService(
    private val reguleringRepo: ReguleringRepo,
    private val kjoringRepo: KjoringRepo,
    private val transformationRepo: TransformationRepo,
) {
    suspend fun createParameterWriter(kjoringId: Int) : (suspend (Writer) -> Unit)? {
        val kjoring = withContext(Dispatchers.IO) {
            kjoringRepo.getKjoring(kjoringId)
        } ?: return null

        val regulering = reguleringRepo.getReguleringById(kjoring.regulering) ?: return null

        return { writer ->
            val parametere = when (regulering) {
                is LegacyRegulering -> legacyParametere(regulering)
                is ReformOpRegulering -> reformOpParametere(regulering)
                else -> throw IllegalStateException("Unknown regulering type: ${regulering::class.qualifiedName}")
            }

            skrivParametere(writer, parametere)
        }
    }

    private fun legacyParametere(regulering: LegacyRegulering): StedsnavnParametere {
        val input = regulering.toReguleringsinput()

        return StedsnavnParametere(
            ikrafttredelsesdato = input.ikrafttredelsesdato,
            nyeFylker = input.fylker.map { it.fylkesnummer to it.fylkesnavn.name },
            nyeKommuner = input.kommuner.map { it.kommunenummer to it.kommunenavn.name },
            utgåendeKommuner = input.endringer.filterIsInstance<Kommuneendring>().map {
                val fra = Kommunenummer(it.fylkesnummer.fra, it.kommuneløpenummer.fra)
                val til = Kommunenummer(it.fylkesnummer.til.first(), it.kommuneløpenummer.til.first())
                fra to til
            },
            utgåendeFylker = input.endringer.filterIsInstance<Fylkeendring>().map { it.fylkesnummer.fra },
        )
    }

    private fun reformOpParametere(regulering: ReformOpRegulering): StedsnavnParametere {
        val claims = ReformClaims.validate(regulering.ops).getOrElse { feil ->
            throw IllegalStateException("Ugyldig regulering" + feil.joinToString("\n\t- ", ":\n\t- ") { it.message })
        }

        val nyeFylker = mutableListOf<Pair<Fylkesnummer, String>>()
        val nyeKommuner = mutableListOf<Pair<Kommunenummer, String>>()
        claims.declarations.filterIsInstance<ReformOp.NewEntity>().forEach { op ->
            op.target.somFylkesnummer()?.let { nyeFylker += it to op.payload<Fylkesdata>().navn }
            op.target.somKommunenummer()?.let { nyeKommuner += it to op.payload<Kommunedata>().navn }
        }

        val utgåendeFylker = mutableListOf<Fylkesnummer>()
        val utgåendeKommuner = mutableListOf<Pair<Kommunenummer, Kommunenummer>>()
        claims.declarations.filterIsInstance<ReformOp.SourceOp>().forEach { op ->
            val fylke = op.source.somFylkesnummer()
            val kommune = op.source.somKommunenummer()
            if (fylke == null && kommune == null) return@forEach

            // Move beholder entiteten og gir ingen nytt navn, så den kan ikke uttrykkes med UF/UK
            require(op !is ReformOp.Move) { "Move av fylke eller kommune støttes ikke i stedsnavn-parameterne: $op" }

            fylke?.let { utgåendeFylker += it }
            kommune?.let { fra ->
                val til = op.succesor()?.somKommunenummer()
                    ?: throw IllegalArgumentException("Kommunen ${fra.verdi()} utgår uten etterfølgende kommune: $op")
                utgåendeKommuner += fra to til
            }
        }

        return StedsnavnParametere(
            ikrafttredelsesdato = regulering.ikrafttredelse,
            nyeFylker = nyeFylker,
            nyeKommuner = nyeKommuner,
            utgåendeKommuner = utgåendeKommuner,
            utgåendeFylker = utgåendeFylker,
        )
    }

    private fun skrivParametere(writer: Writer, parametere: StedsnavnParametere) {
        writer.appendLine("DT\t${parametere.ikrafttredelsesdato}")

        parametere.nyeFylker.sortedBy { it.first }.forEach { (fylkesnummer, navn) ->
            writer.appendLine("NF\t${fylkesnummer.verdi()}\t$navn")
        }

        parametere.nyeKommuner.sortedBy { it.first }.forEach { (kommunenummer, navn) ->
            writer.appendLine("NK\t${kommunenummer.verdi()}\t$navn")
        }

        writer.appendLine("AN\t<<JSON her>>")

        parametere.utgåendeKommuner.sortedBy { it.first }.forEach { (fra, til) ->
            writer.appendLine("UK\t${fra.verdi()}\t${til.verdi()}")
        }

        parametere.utgåendeFylker.sorted().forEach { fra ->
            writer.appendLine("UF\t${fra.verdi()}")
        }
    }

    suspend fun createSsrJsonWriter(context: KrAppBootContext, kjoringId: Int) : (suspend (Writer) -> Unit)? {
        val kjoring = withContext(Dispatchers.IO) {
            kjoringRepo.getKjoring(kjoringId)
        } ?: return null

        val contextForMottaker = if (
            context.config.hasPath("feature.test.integration") && context.config.getBoolean("feature.test.integration")
        ) {
            context
        } else {
            object : KrAppBootContext {
                override val config: Config = ConfigFactory.parseMap(
                    mapOf(
                        "integration.matrikkel.mottaker.user" to env(kjoring.mottaker.toString() + "_USERNAME"),
                        "integration.matrikkel.mottaker.password" to env(kjoring.mottaker.toString() + "_PASSWORD"),
                    )
                )
                    .withFallback(context.config)
            }
        }

        val flow = transformationRepo.readTransformationFromDatabase(kjoringId, "Veg")

        return { writer ->
            ssrJson(contextForMottaker, flow, writer)
        }
    }

    private fun env(v: String?): String {
        if (v == null || env[v] == null) {
            logger.warn("Mangler miljøvariabel-verdi for $v")
            return "ENV_MISSING"
        }
        return env[v]
    }
}

/** Innholdet i parameterfilen, uavhengig av hvilken reguleringstype den er utledet fra. */
private data class StedsnavnParametere(
    val ikrafttredelsesdato: LocalDate,
    val nyeFylker: List<Pair<Fylkesnummer, String>>,
    val nyeKommuner: List<Pair<Kommunenummer, String>>,
    val utgåendeKommuner: List<Pair<Kommunenummer, Kommunenummer>>,
    val utgåendeFylker: List<Fylkesnummer>,
)

private val fylkeIdentKomponenter: List<KClass<*>> = listOf(Fylkesnummer::class)
private val kommuneIdentKomponenter: List<KClass<*>> = listOf(Fylkesnummer::class, Kommunenummer.Lopenummer::class)

private fun Ident.NonEmpty.somFylkesnummer(): Fylkesnummer? =
    if (type.types.map { it.classifier } == fylkeIdentKomponenter) toArray()[0] as Fylkesnummer else null

private fun Ident.NonEmpty.somKommunenummer(): Kommunenummer? =
    if (type.types.map { it.classifier } == kommuneIdentKomponenter) {
        toArray().let { Kommunenummer(it[0] as Fylkesnummer, it[1] as Kommunenummer.Lopenummer) }
    } else {
        null
    }

private inline fun <reified P : Payload> ReformOp.NewEntity.payload(): P {
    val innhold: Payload? = when (this) {
        is ReformOp.Create -> payload
        is ReformOp.Derive -> payload
    }
    return innhold as? P
        ?: throw IllegalArgumentException("Forventet ${P::class.simpleName} som payload for $target, men fikk $innhold")
}

private fun ReformOp.SourceOp.succesor(): Ident.NonEmpty? = when (this) {
    is ReformOp.RetireTo -> target
    is ReformOp.Retire -> successor
    is ReformOp.Move -> target
}
