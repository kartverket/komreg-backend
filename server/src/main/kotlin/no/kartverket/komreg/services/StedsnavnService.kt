package no.kartverket.komreg.services

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.verdi
import no.kartverket.komreg.env
import no.kartverket.komreg.logger
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.ReformReguleringRepo
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.routes.toReguleringsinput
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.transformation.Fylkeendring
import no.kartverket.komreg.transformation.Kommuneendring
import no.kartverket.komreg.transformation.Reguleringsinput
import arrow.core.getOrElse
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.ReformOp
import no.statkart.matrikkel.komreg.stedsnavn.ssrJson
import java.io.Writer

class StedsnavnService(
    private val reguleringRepo: ReguleringRepo,
    private val reformReguleringRepo: ReformReguleringRepo,
    private val kjoringRepo: KjoringRepo,
    private val transformationRepo: TransformationRepo,
) {
    suspend fun createParameterWriter(kjoringId: Int) : (suspend (Writer) -> Unit)? {
        val kjoring = withContext(Dispatchers.IO) {
            kjoringRepo.getKjoring(kjoringId)
        } ?: return null

        val linjer = withContext(Dispatchers.IO) {
            when {
                kjoring.regulering != null ->
                    reguleringRepo.getReguleringById(kjoring.regulering)
                        ?.toReguleringsinput()
                        ?.stedsnavnParametre()

                kjoring.reformRegulering != null ->
                    reformReguleringRepo.get(kjoring.reformRegulering)
                        ?.let { regulering ->
                            val claims = ReformClaims.validate(regulering.regler).getOrElse { errs ->
                                throw IllegalStateException(
                                    "Reform-reguleringen ${kjoring.reformRegulering} gir ugyldige regler: " +
                                        errs.joinToString { it.message },
                                )
                            }
                            stedsnavnParametre(claims, regulering.ikrafttredelsesdato)
                        }

                else -> null
            }
        } ?: return null

        return { writer -> linjer.forEach { writer.appendLine(it) } }
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

/** Parametrene til stedsnavnjobben for en `legacy`-regulering. Kommunenavn skrives med store bokstaver. */
internal fun Reguleringsinput.stedsnavnParametre(): List<String> = buildList {
    add("DT\t$ikrafttredelsesdato")

    fylker.sortedBy { it.fylkesnummer }.forEach {
        add("NF\t${it.fylkesnummer.verdi()}\t${it.fylkesnavn.name}")
    }

    kommuner.sortedBy { it.kommunenummer }.forEach {
        add("NK\t${it.kommunenummer.verdi()}\t${it.kommunenavn.name.uppercase()}")
    }

    add("AN\t<<JSON her>>")

    endringer.filterIsInstance<Kommuneendring>()
        .map {
            val fra = Kommunenummer(it.fylkesnummer.fra, it.kommuneløpenummer.fra)
            val til = Kommunenummer(it.fylkesnummer.til.first(), it.kommuneløpenummer.til.first())
            fra to til
        }
        .sortedBy { it.first }
        .forEach { (fra, til) -> add("UK\t${fra.verdi()}\t${til.verdi()}") }

    endringer.filterIsInstance<Fylkeendring>()
        .map { it.fylkesnummer.fra }
        .sorted()
        .forEach { fra -> add("UF\t${fra.verdi()}") }
}

/**
 * Parametrene til stedsnavnjobben for en `reform`-regulering, utledet fra de validerte reglene
 * ([ReformClaims.declarations], der identiske duplikater er slått sammen):
 *
 * - `NF`/`NK`: fylker og kommuner som opprettes ([ReformOp.Create]/[ReformOp.Derive]). Navnet hentes fra
 *   payloaden; en [ReformOp.Derive] uten payload gir tomt navn. Kommunenavn skrives med store bokstaver.
 * - `UK`: kommuner som utgår, med målet ([ReformOp.RetireTo]) eller etterfølgeren ([ReformOp.Retire]).
 * - `UF`: fylker som utgår.
 *
 * Forskjell fra `legacy`: et eksisterende mål for en sammenslåing opprettes ikke, og står derfor ikke under
 * `NF`/`NK`.
 */
internal fun stedsnavnParametre(claims: ReformClaims, ikrafttredelsesdato: LocalDate): List<String> = buildList {
    add("DT\t$ikrafttredelsesdato")

    val regler = claims.declarations

    val nye = regler.mapNotNull { op ->
        when (op) {
            is ReformOp.Create -> op.target to op.payload
            is ReformOp.Derive -> op.target to op.payload
            else -> null
        }
    }

    nye.mapNotNull { (target, payload) ->
        target.fylkesnummer()?.let { it to (payload as? Fylkesdata)?.navn.orEmpty() }
    }
        .sortedBy { it.first }
        .forEach { (nr, navn) -> add("NF\t${nr.verdi()}\t$navn") }

    nye.mapNotNull { (target, payload) ->
        target.kommunenummer()?.let { it to (payload as? Kommunedata)?.navn.orEmpty() }
    }
        .sortedBy { it.first }
        .forEach { (nr, navn) -> add("NK\t${nr.verdi()}\t${navn.uppercase()}") }

    add("AN\t<<JSON her>>")

    regler.mapNotNull { op ->
        when (op) {
            is ReformOp.RetireTo -> op.source.kommunenummer()?.let { fra -> op.target.kommunenummer()?.let { fra to it } }
            is ReformOp.Retire -> op.source.kommunenummer()?.let { fra -> op.successor?.kommunenummer()?.let { fra to it } }
            else -> null
        }
    }
        .sortedBy { it.first }
        .forEach { (fra, til) -> add("UK\t${fra.verdi()}\t${til.verdi()}") }

    regler.mapNotNull { op ->
        when (op) {
            is ReformOp.RetireTo -> op.source.fylkesnummer()
            is ReformOp.Retire -> op.source.fylkesnummer()
            else -> null
        }
    }
        .sorted()
        .forEach { add("UF\t${it.verdi()}") }
}

private fun Ident.NonEmpty.fylkesnummer(): Fylkesnummer? =
    toArray().singleOrNull() as? Fylkesnummer

private fun Ident.NonEmpty.kommunenummer(): Kommunenummer? {
    val deler = toArray()
    if (deler.size != 2) return null
    val fylke = deler[0] as? Fylkesnummer ?: return null
    val lopenummer = deler[1] as? Kommunenummer.Lopenummer ?: return null
    return Kommunenummer(fylke, lopenummer)
}
