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
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.transformation.Fylkeendring
import no.kartverket.komreg.transformation.Kommuneendring
import no.statkart.matrikkel.komreg.stedsnavn.ssrJson
import java.io.Writer

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
            val input = regulering.toReguleringsinput()

            writer.appendLine("DT\t${input.ikrafttredelsesdato}")

            input.fylker.sortedBy { it.fylkesnummer }.forEach {
                writer.appendLine("NF\t${it.fylkesnummer.verdi()}\t${it.fylkesnavn.name}")
            }

            input.kommuner.sortedBy { it.kommunenummer }.forEach {
                writer.appendLine("NK\t${it.kommunenummer.verdi()}\t${it.kommunenavn.name}")
            }

            writer.appendLine("AN\t<<JSON her>>")

            input.endringer.filterIsInstance<Kommuneendring>()
                .map {
                    val fra = Kommunenummer(it.fylkesnummer.fra, it.kommuneløpenummer.fra)
                    val til = Kommunenummer(it.fylkesnummer.til.first(), it.kommuneløpenummer.til.first())
                    fra to til
                }
                .sortedBy { it.first }
                .forEach { (fra, til) ->
                    writer.appendLine("UK\t${fra.verdi()}\t${til.verdi()}")
                }

            input.endringer.filterIsInstance<Fylkeendring>()
                .map { it.fylkesnummer.fra }
                .sorted()
                .forEach { fra ->
                    writer.appendLine("UF\t${fra.verdi()}")
                }
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
