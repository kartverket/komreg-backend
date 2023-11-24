package no.kartverket.komreg.transformation

import arrow.core.getOrElse
import arrow.core.mapOrAccumulate
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface Storage {
    fun writeTransformationsToDatabase(kjoringId: Int, transformResultList: List<Transformation>)

    fun readTransformationsFromDatabase(kjoringId: Int): Flow<Transformation>

    fun readTransformationOfTypeFromDatabase(kjoringId: Int, type: String): Flow<Transformation>

    fun createTilbakeforingsstatusForKjoring(kjoringId: Int, entitySinks: List<EntitySink>)

    fun setTilbakeforingsStatusForSink(sink: EntitySink, status: String, kjoringId: Int, erOppretting: Boolean)
    fun hentIkkeStartedeTilbakeforingerForNyeEntiteter(kjoringId: Int): List<String>

    fun hentIkkeStartedeTilbakeforingerForErstattendeEntiteter(kjoringId: Int): List<String>

    fun setStatusForKjoring(kjoringId: Int, status: String)
}

suspend fun transform(
    kjoringId: Int,
    input: Reguleringsinput,
    lifeCycleHandlers: List<LifeCycleHandler>,
    entitySources: List<EntitySource>,
    entityProcessors: List<EntityProcessor>,
    entitySinks: List<EntitySink>,
    idGeneratorManager: IdGeneratorManager,
    storage: Storage,
    skalTilbakefores: Boolean,
) {
    val logger: Logger = LoggerFactory.getLogger(Transformation::class.java)
    val transformer = IdentTransformer(mapInput(input))

    val gjenvarendeSinkerForNyeEntiteter = storage.hentIkkeStartedeTilbakeforingerForNyeEntiteter(kjoringId)
    val gjenvarendeSinkerForErstattendeEntiteter =
        storage.hentIkkeStartedeTilbakeforingerForErstattendeEntiteter(kjoringId)

    logger.info("gjenvarendeSinkerForNyeEntiteter: $gjenvarendeSinkerForNyeEntiteter")
    logger.info("gjenvarendeSinkerForErstattendeEntiteter: $gjenvarendeSinkerForErstattendeEntiteter")

    if (gjenvarendeSinkerForNyeEntiteter.isNotEmpty() && gjenvarendeSinkerForErstattendeEntiteter.isNotEmpty()) {
        logger.info("starter skriving av transformasjoner for kjøring $kjoringId")
        lifeCycleHandlers.forEach { it.beforeRun(!skalTilbakefores) }

        entitySources.forEach { entitySource ->
            logger.info("Starter transformasjon for ${entitySource.id}")
            val flow = entitySource.entityFlow

            val transformResult = flow
                .mapNotNull { entity ->
                    val result = transformer.transform(entity, idGeneratorManager::idFor)
                    result
                }

            val transformResultFlow = transformResult.transform { list ->
                list.forEach { emit(it) }
            }
            transformResultFlow.chunked(10000)
                .collect { chunk ->

                    storage.writeTransformationsToDatabase(kjoringId, chunk)
                }
            logger.info("Ferdig med transformasjon for ${entitySource.id}")
        }
    }

    entityProcessors.forEach { processor ->
        logger.info("1")
        storage.readTransformationsFromDatabase(kjoringId)
            .collect { processor.consume(it) }
        logger.info("2")
        val result = processor.produce()
        logger.info("3")

        result.chunked(10000)
            .collect { chunk ->
                storage.writeTransformationsToDatabase(kjoringId, chunk)
            }
        // storage.writeTransformationsToDatabase(kjoringId, result.toList())
        logger.info("4")
    }

    val transformations = storage.readTransformationsFromDatabase(kjoringId)

    if (skalTilbakefores) {
        // Kjør ut alle nyopprettinger
        storage.setStatusForKjoring(kjoringId, "STARTET_TILBAKEFØRING")

        entitySinks.forEach { sink ->

            if (gjenvarendeSinkerForNyeEntiteter.contains(sink.id)) {
                try {
                    storage.setTilbakeforingsStatusForSink(sink, "TILBAKEFØRER", kjoringId, erOppretting = true)
                    sink.consumeTransformations(
                        transformations.filter {
                            val sourceEntity = it.sourceEntity
                            sourceEntity == null || sourceEntity.id != it.id
                        },
                        input.ikrafttredelsesdato.toJavaLocalDate(),

                    )
                    storage.setTilbakeforingsStatusForSink(sink, "FERDIG", kjoringId, erOppretting = true)
                } catch (e: Exception) {
                    storage.setTilbakeforingsStatusForSink(sink, "FEILET", kjoringId, erOppretting = true)
                    storage.setStatusForKjoring(kjoringId, "TILBAKEFØRING_FEILET")
                    throw e
                }
            }
        }

        // Kjør ut resten
        // TODO: Hva med "slettinger"
        entitySinks.forEach { sink ->
            if (gjenvarendeSinkerForErstattendeEntiteter.contains(sink.id)) {
                try {
                    storage.setTilbakeforingsStatusForSink(sink, "TILBAKEFØRER", kjoringId, erOppretting = false)
                    sink.consumeTransformations(
                        transformations.filter {
                            val sourceEntity = it.sourceEntity
                            sourceEntity != null && sourceEntity.id == it.id
                        },
                        input.ikrafttredelsesdato.toJavaLocalDate(),
                    )
                    storage.setTilbakeforingsStatusForSink(sink, "FERDIG", kjoringId, erOppretting = false)
                } catch (e: Exception) {
                    storage.setTilbakeforingsStatusForSink(sink, "FEILET", kjoringId, erOppretting = false)
                    storage.setStatusForKjoring(kjoringId, "TILBAKEFØRING_FEILET")
                    throw e
                }
            }
        }

        storage.setStatusForKjoring(kjoringId, "FULLFØRT_TILBAKEFØRING")
    } else {
        transformations.collect()
        storage.setStatusForKjoring(kjoringId, "IKKE_TILBAKEFØRT")
    }

    entitySinks
        .mapOrAccumulate { sink ->
            sink.postTransformValidate()
                .mapLeft { errs -> errs.map { sink::class.java.name to it } }.bindNel()
        }
        .getOrElse { errs ->
            errs.groupBy({ it.first }, { it.second })
                .forEach { (sinkName, errs) ->
                    val logger = LoggerFactory.getLogger(sinkName)
                    errs.forEach { err ->
                        when (err) {
                            is TransformValidationError.ForIdent ->
                                logger.error("Valideringsfeil {}: {}", err.ident, err.message)
                            is TransformValidationError.UncaughtThrowable ->
                                logger.error("VALIDERING IKKE UTFØRT PGA FEIL: ${err.message}", err.throwable)
                        }
                    }
                }
        }

    lifeCycleHandlers.forEach { it.afterRun(!skalTilbakefores) }
}

suspend fun mapInput(input: Reguleringsinput): List<Pair<Ident, IdentTransformer.Mapping>> {
    val kommuneMap = input.kommuner.associateBy { it.kommunenummer }
    val fylkeMap = input.fylker.associateBy { it.fylkesnummer }

    return input.endringer.map { m ->
        when (m) {
            is Fylkeendring -> mapFylkeendring(m, fylkeMap)
            is Kommuneendring -> mapKommuneendring(m, kommuneMap, input.ikrafttredelsesdato)
            is Kretsendring -> mapKretsendring(m)
            is Matrikkelenhetendring -> mapMatrikkelenhetendring(m)
            is Teigendring -> mapTeig(m)
            is Vegadresseendring -> mapVegadresseendring(m)
            is Vegendring -> mapVegendring(m)
        }
    }
}

private suspend fun mapFylkeendring(
    fylkeendring: Fylkeendring,
    fylkeMap: Map<Fylkesnummer, Fylke>,
): Pair<Ident, IdentTransformer.Mapping> {
    val fylkeIdentType: IdentType1<Fylkesnummer> = identTypeOf1()

    val til = fylkeendring.fylkesnummer.til.map { tilFnr ->
        val fylke = fylkeMap.getValue(tilFnr)
        val payload = fylke.tilFylkesdata()
        return@map fylkeIdentType(tilFnr) to payload
    }

    return fylkeIdentType(fylkeendring.fylkesnummer.fra) to if (til.size == 1) {
        val t = til[0]
        IdentTransformer.Mapping.Replace(
            t.first,
            t.second,
        )
    } else {
        IdentTransformer.Mapping.Split(
            listOf(
                Ident.Empty to null, // Ikke sett ny fylke-kobling
            ) + til,
        )
    }
}

private suspend fun mapKommuneendring(
    kommuneendring: Kommuneendring,
    kommuneMap: Map<Kommunenummer, Kommune>,
    ikrafttredelsesdato: LocalDate,
): Pair<Ident, IdentTransformer.Mapping> {
    val kommuneIdentType: IdentType2<Fylkesnummer, Kommunenummer.Lopenummer> = identTypeOf2()

    val til = kommuneendring.kommuneløpenummer.til.map { tilKlnr ->
        val nyttKommunenummer = Kommunenummer(kommuneendring.fylkesnummer.til.single(), tilKlnr)
        val kommune = kommuneMap.getValue(nyttKommunenummer)

        val payload = kommune.tilKommunedata(ikrafttredelsesdato)

        return@map kommuneIdentType(kommuneendring.fylkesnummer.til.single(), tilKlnr) to payload
    }

    return kommuneIdentType(kommuneendring.fylkesnummer.fra, kommuneendring.kommuneløpenummer.fra) to if (til.size == 1) {
        val t = til[0]
        IdentTransformer.Mapping.Replace(
            t.first,
            t.second,
        )
    } else {
        IdentTransformer.Mapping.Split(
            listOf(
                Ident.Empty to null, // Ikke sett ny kommune-kobling
            ) + til,
        )
    }
}

suspend fun mapMatrikkelenhetendring(matrikkelenhetendring: Matrikkelenhetendring): Pair<Ident, IdentTransformer.Mapping> {
    val gardsnummerIdentType = identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Matrikkelnummer.Gardsnummer>()

    return gardsnummerIdentType(
        matrikkelenhetendring.fylkesnummer.fra,
        matrikkelenhetendring.kommuneløpenummer.fra,
        matrikkelenhetendring.gårdsnummer.fra,
    ) to IdentTransformer.Mapping.Simple(
        gardsnummerIdentType(
            matrikkelenhetendring.fylkesnummer.til,
            matrikkelenhetendring.kommuneløpenummer.til,
            matrikkelenhetendring.gårdsnummer.til,
        ),
    )
}

suspend fun mapTeig(teigendring: Teigendring): Pair<Ident, IdentTransformer.Mapping> {
    val teigIdentType = identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, TeigId>()

    return teigIdentType(
        teigendring.fylkesnummer.fra,
        teigendring.kommuneløpenummer.fra,
        teigendring.teigId.fra,
    ) to IdentTransformer.Mapping.Simple(
        teigIdentType(
            teigendring.fylkesnummer.til,
            teigendring.kommuneløpenummer.til,
            teigendring.teigId.til,
        ),
    )
}

suspend fun mapVegendring(vegendring: Vegendring): Pair<Ident, IdentTransformer.Mapping> {
    val adresseparsellIdentType = identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode>()

    val til = vegendring.kommuneløpenummer.til.map { tilKnln ->
        adresseparsellIdentType(
            vegendring.fylkesnummer.til.single(),
            tilKnln,
            vegendring.adressekode.til,
        ) to null
    }

    return adresseparsellIdentType(
        vegendring.fylkesnummer.fra,
        vegendring.kommuneløpenummer.fra,
        vegendring.adressekode.fra,
    ) to if (til.size == 1) {
        val t = til[0]
        IdentTransformer.Mapping.Simple(
            t.first,
            t.second,
        )
    } else {
        IdentTransformer.Mapping.Split(til)
    }
}

suspend fun mapVegadresseendring(vegadresseendring: Vegadresseendring): Pair<Ident, IdentTransformer.Mapping> {
    val vegadresseIdentType = identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode, Adressenummernummer>()

    return vegadresseIdentType(
        vegadresseendring.fylkesnummer.fra,
        vegadresseendring.kommuneløpenummer.fra,
        vegadresseendring.adressekode.fra,
        vegadresseendring.adressenummer.fra,
    ) to IdentTransformer.Mapping.Simple(
        vegadresseIdentType(
            vegadresseendring.fylkesnummer.til,
            vegadresseendring.kommuneløpenummer.til,
            vegadresseendring.adressekode.til,
            vegadresseendring.adressenummer.til,
        ),
    )
}

suspend fun mapKretsendring(kretsendring: Kretsendring): Pair<Ident, IdentTransformer.Mapping> {
    val kretsIdentType = identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Kretstype, Kretsnummer>()

    return kretsIdentType(
        kretsendring.fylkesnummer.fra,
        kretsendring.kommuneløpenummer.fra,
        kretsendring.kretstype.fra,
        kretsendring.kretsnummer.fra,
    ) to IdentTransformer.Mapping.Simple(
        kretsIdentType(
            kretsendring.fylkesnummer.til,
            kretsendring.kommuneløpenummer.til,
            kretsendring.kretstype.til,
            kretsendring.kretsnummer.til,
        ),
    )
}

fun <T> Flow<T>.chunked(size: Int): Flow<List<T>> = flow {
    val list = ArrayList<T>(size)
    collect {
        list.add(it)
        if (list.size == size) {
            emit(list.toList())
            list.clear()
        }
    }
    if (list.isNotEmpty()) {
        emit(list.toList())
    }
}
