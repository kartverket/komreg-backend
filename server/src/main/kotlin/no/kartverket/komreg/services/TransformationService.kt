package no.kartverket.komreg.services

import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaLocalDate
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.core.domain.PostadresseForOppretting
import no.kartverket.komreg.integration.EntitySinkManager
import no.kartverket.komreg.integration.EntitySourceManager
import no.kartverket.komreg.integration.KommuneServiceManager
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.logger
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.transformation.*
import java.lang.RuntimeException

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

fun transformEntities(
    input: Reguleringsinput,
    kjoringId: Int,
    transformationRepo: TransformationRepo,
    kjoringRepo: KjoringRepo,
) {
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
    val idGeneratorManager = IdGeneratorManager(bootContext)

    printMemoryUsage()

    runAndWriteTransformations(
        bootContext,
        transformStatus,
        input,
        entitySinks,
        kjoringId,
        transformationRepo,
        kjoringRepo,
    )
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
                    transformedIdent = Ident(fylke.fylkesnummer),
                    resultObject = Fylkesdata(fylke.fylkesnavn.name),
                ),
            )
        }
    }

    logger.info("Starter tilbakeføring av fylker")
    entitySinks.consume(transformedFylker, input.ikrafttredelsesdato.toJavaLocalDate())
    logger.info("Fullført tilbakeføring av fylker")
}

private suspend fun writeKommuner(
    bootContext: KrAppBootContext,
    input: Reguleringsinput,
    entitySinks: EntitySinkManager,
    kjoringId: Int,
    transformationRepo: TransformationRepo,
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
                    transformedIdent = Ident(kommune.kommunenummer.fylkesnummer, kommune.kommunenummer.lopenummer),
                    resultObject = Kommunedata(
                        navn = kommune.kommunenavn.name,
                        koordinatsystem = kommune.koordinatsystem,
                        senterpunkt = kommune.senterpunkt,
                        nedsattKonsesjonsgrense = kommune.nedsattKonsesjonsgrense,
                        godkjenteGardsnumre = kommune.godkjenteGardsnumre,
                        adresse = kommune.adresse?.let {
                            PostadresseForOppretting(
                                adresselinje1 = it.adresselinje1?.trim()?.ifEmpty { null },
                                adresselinje2 = it.adresselinje2?.trim()?.ifEmpty { null },
                                postnummer = it.postnummer,
                            )
                        },
                        standardRekvirentOrgnummer = kommune.standardRekvirent?.orgnummer,
                        kommunevapen = kommune.kommunevapen,
                        ikrafttredelsesdato = input.ikrafttredelsesdato,
                    ),
                ),
            )
        }
    }

    logger.info("Starter tilbakeføring av kommuner")
    entitySinks.consume(transformedKommuner, input.ikrafttredelsesdato.toJavaLocalDate())
    transformationRepo.writeTransformationsToDatabase(kjoringId, transformedKommuner.toList())
    logger.info("Fullført tilbakeføring av kommuner")
}

private fun runAndWriteTransformations(
    bootContext: KrAppBootContext,
    transformStatus: TransformationStatusForRegulering,
    input: Reguleringsinput,
    entitySinks: EntitySinkManager,
    kjoringId: Int,
    transformationRepo: TransformationRepo,
    kjoringRepo: KjoringRepo,

) {
    val sources = EntitySourceManager(bootContext).entitySources

    val idGeneratorManager = IdGeneratorManager(bootContext)
    val mappings = reguleringsinputToMappings(input)
    val identTransformer = IdentTransformer(*mappings.toTypedArray())

    CoroutineScope(Dispatchers.IO).launch {
        if (input.fylker.isNotEmpty()) {
            writeFylker(bootContext, input, entitySinks)
        }

        if (input.kommuner.isNotEmpty()) {
            writeKommuner(bootContext, input, entitySinks, kjoringId, transformationRepo)
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
                .mapNotNull { entity -> identTransformer.transform(entity, idGeneratorManager) }
                .onEach {
                    statusForSource.numberOfTransformations += 1
                }
                .onCompletion {
                    logger.info("Fullført transformasjoner for flow av type $type")
                    statusForSource.transformationFinished = Clock.System.now()
                }

            val transformResultList = transformResult.toList().flatten()
            val newFlow: Flow<Transformation> = transformResultList.asFlow()

            logger.info("Starter tilbakeføring fra source: $type")
            entitySinks.consume(newFlow, input.ikrafttredelsesdato.toJavaLocalDate())

            transformationRepo.writeTransformationsToDatabase(kjoringId, transformResultList)

            logger.info("Fullført tilbakeføring av source: $type")
            statusForSource.tilbakeføringFinished = Clock.System.now()
        }
        transformStatus.finish()
        kjoringRepo.updateKjoringEndTime(kjoringId)
        logger.info("Avsluttet alle transformasjoner!")
    }
}

private fun reguleringsinputToMappings(reguleringsinput: Reguleringsinput): List<Pair<Ident, Ident?>> {
    return runBlocking {
        reguleringsinput.endringer.map { endring ->
            when (endring) {
                is Fylkeendring -> Ident(endring.fylkesnummer.fra) to Ident(endring.fylkesnummer.til)
                is Kommuneendring -> Ident(endring.fylkesnummer.fra, endring.kommuneløpenummer.fra) to Ident(
                    endring.fylkesnummer.til,
                    endring.kommuneløpenummer.til,
                )

                is Matrikkelenhetendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.gårdsnummer.fra,
                ) to Ident(
                    endring.fylkesnummer.til,
                    endring.kommuneløpenummer.til,
                    endring.gårdsnummer.til,
                )

                is Kretsendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.kretsnummer.fra,
                    endring.kretstype.fra,
                ) to Ident(
                    endring.fylkesnummer.til,
                    endring.kommuneløpenummer.til,
                    endring.kretsnummer.til,
                    endring.kretstype.til,
                )
                /*is Vegendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.adressekode.fra,
                ) to Ident(
                    endring.fylkesnummer.til,
                    endring.kommuneløpenummer.til,
                    endring.adressekode.til,
                )*/
                is Teigendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.teigId.fra,
                ) to Ident(
                    endring.fylkesnummer.til,
                    endring.kommuneløpenummer.til,
                    endring.teigId.til,
                )

                is Vegadresseendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.adressekode.fra,
                    endring.adressenummer.fra,
                    endring.adressenummerbokstav.fra,
                ) to Ident(
                    endring.fylkesnummer.til,
                    endring.kommuneløpenummer.til,
                    endring.adressekode.til,
                    endring.adressenummer.til,
                    endring.adressenummerbokstav.til,
                )

                else -> throw RuntimeException("Ukjent endringstype: ${endring::class.simpleName}")
            }
        }
    }
}
