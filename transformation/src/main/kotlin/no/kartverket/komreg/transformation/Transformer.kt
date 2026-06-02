package no.kartverket.komreg.transformation

import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.mapOrAccumulate
import arrow.core.toNonEmptyListOrNull
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.core.domain.Matrikkelenhet.GardsnummerserieIdent
import no.kartverket.komreg.core.domain.Matrikkelenhet.GrunneiendomIdent
import no.kartverket.komreg.integration.spi.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface Storage {
    fun writeTransformationsToDatabase(
        kjoringId: Int,
        transformResultList: List<Transformation>,
    )

    fun readTransformationsFromDatabase(kjoringId: Int): Flow<Transformation>

    fun createTilbakeforingsstatusForKjoring(
        kjoringId: Int,
        entitySinks: List<EntitySink>,
    )

    fun setTilbakeforingsStatusForSink(
        sink: EntitySink,
        status: String,
        kjoringId: Int,
        erOppretting: Boolean,
    )

    fun hentSinkerSomSkalGjenopptasForNyeEntiteter(kjoringId: Int): List<String>

    fun hentSinkerSomSkalGjenopptasForErstattendeEntiteter(kjoringId: Int): List<String>

    fun setStatusForKjoring(
        kjoringId: Int,
        status: String,
    )
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
    erForstegangskjoring: Boolean = true,
) {
    val logger: Logger = LoggerFactory.getLogger({}::class.java)
    val transformer = IdentTransformer(TODO())

    val gjenvarendeSinkerForNyeEntiteter = storage.hentSinkerSomSkalGjenopptasForNyeEntiteter(kjoringId)
    val gjenvarendeSinkerForErstattendeEntiteter =
        storage.hentSinkerSomSkalGjenopptasForErstattendeEntiteter(kjoringId)

    logger.info("gjenvarendeSinkerForNyeEntiteter: $gjenvarendeSinkerForNyeEntiteter")
    logger.info("gjenvarendeSinkerForErstattendeEntiteter: $gjenvarendeSinkerForErstattendeEntiteter")

    if (erForstegangskjoring) {
        logger.info("Starter skriving av transformasjoner for kjøring $kjoringId")
        lifeCycleHandlers.forEach { it.beforeRun(!skalTilbakefores) }

        entitySources.forEach { entitySource ->
            logger.info("Skriver transformasjon for ${entitySource.id}")
            val flow = entitySource.entityFlow

            val transformResult =
                flow
                    .mapNotNull { entity ->
                        val result = transformer.transform(entity, idGeneratorManager::idFor)
                        result
                    }

            val transformResultFlow =
                transformResult.transform { list ->
                    list.forEach { emit(it) }
                }
            transformResultFlow.chunked(10000)
                .collect { chunk ->

                    storage.writeTransformationsToDatabase(kjoringId, chunk)
                }
            logger.info("Ferdigskrevet transformasjoner for ${entitySource.id}")
        }

        entityProcessors.forEach { processor ->
            logger.info("Starter å lese transformasjoner for processor $processor")
            storage.readTransformationsFromDatabase(kjoringId)
                .collect { processor.consume(it) }
            val result = processor.produce()

            result.chunked(10000)
                .collect { chunk ->
                    storage.writeTransformationsToDatabase(kjoringId, chunk)
                }

            logger.info("Ferdigskrevet transformasjoner for processor")
        }
    }

    val transformationList = storage.readTransformationsFromDatabase(kjoringId).toList()
    val transformations = transformationList.asFlow()

    if (skalTilbakefores) {
        // Kjør ut alle nyopprettinger
        storage.setStatusForKjoring(kjoringId, "STARTET_TILBAKEFØRING")
        logger.info("Starter tilbakeføring av ${transformations.count()} transformasjoner")

        entitySinks.forEach { sink ->

            if (gjenvarendeSinkerForNyeEntiteter.contains(sink.id)) {
                try {
                    storage.setTilbakeforingsStatusForSink(sink, "TILBAKEFØRER", kjoringId, erOppretting = true)
                    logger.info("Tilbakefører nyopprettinger for ${sink.id}")
                    sink.consumeTransformations(
                        transformations.filter {
                            val sourceEntity = it.sourceEntity
                            sourceEntity == null || sourceEntity.id != it.id
                        },
                        input.ikrafttredelsesdato.toJavaLocalDate(),
                    )
                    logger.info("Tilbakeført nyopprettinger for ${sink.id}")
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
                    logger.info("Tilbakefører endringer for ${sink.id}")
                    sink.consumeTransformations(
                        transformations.filter {
                            val sourceEntity = it.sourceEntity
                            sourceEntity != null && sourceEntity.id == it.id
                        },
                        input.ikrafttredelsesdato.toJavaLocalDate(),
                    )
                    logger.info("Tilbakeført endringer for ${sink.id}")
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
        logger.info("Antall transformasjoner som ikke ble tilbakeført: ${transformationList.size}")
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
                                logger.error("VALIDERING IKKE UTFØRT PGA FEIL: $err", err.throwable)
                        }
                    }
                }
        }

    lifeCycleHandlers.forEach { it.afterRun(!skalTilbakefores) }
}

//suspend fun mapInput(input: Reguleringsinput): List<Pair<Ident, IdentTransformer.Mapping>> {
//    val kommuneMap = input.kommuner.associateBy { it.kommunenummer }
//    val fylkeMap = input.fylker.associateBy { it.fylkesnummer }
//
//    return input.endringer.flatMap { m ->
//        when (m) {
//            is Fylkeendring -> listOf(mapFylkeendring(m, fylkeMap))
//            is Kommuneendring -> listOf(mapKommuneendring(m, kommuneMap, input.ikrafttredelsesdato))
//            is Kretsendring -> listOf(mapKretsendring(m))
//            is Matrikkelenhetendring -> mapMatrikkelenhetendring(m)
//            is Teigendring -> listOf(mapTeig(m))
//            is Vegadresseendring -> listOf(mapVegadresseendring(m))
//            is Vegendring -> listOf(mapVegendring(m))
//        }
//    }
//}

//private suspend fun mapFylkeendring(
//    fylkeendring: Fylkeendring,
//    fylkeMap: Map<Fylkesnummer, Fylke>,
//): Pair<Ident, IdentTransformer.Mapping> {
//    val fylkeIdentType: IdentType1<Fylkesnummer> = identTypeOf1()
//
//    val til =
//        fylkeendring.fylkesnummer.til.map { tilFnr ->
//            val fylke = fylkeMap.getValue(tilFnr)
//            val payload = fylke.tilFylkesdata()
//            return@map fylkeIdentType(tilFnr) to payload
//        }
//
//    return fylkeIdentType(fylkeendring.fylkesnummer.fra) to
//            if (til.size == 1) {
//                val t = til[0]
//                IdentTransformer.Mapping.Replace(
//                    t.first,
//                    t.second,
//                    fylkeendring.sammenslaaing,
//                )
//            } else {
//                IdentTransformer.Mapping.Split(
//                    listOf(
//                        Ident.Empty to null, // Ikke sett ny fylke-kobling
//                    ) + til,
//                )
//            }
//}

//private suspend fun mapKommuneendring(
//    kommuneendring: Kommuneendring,
//    kommuneMap: Map<Kommunenummer, Kommune>,
//    ikrafttredelsesdato: LocalDate,
//): Pair<Ident, IdentTransformer.Mapping> {
//    val kommuneIdentType: IdentType2<Fylkesnummer, Kommunenummer.Lopenummer> = identTypeOf2()
//
//    val til =
//        kommuneendring.kommuneløpenummer.til.map { tilKlnr ->
//            val nyttKommunenummer = Kommunenummer(kommuneendring.fylkesnummer.til.single(), tilKlnr)
//            val kommune = kommuneMap.getValue(nyttKommunenummer)
//
//            val payload = kommune.tilKommunedata(ikrafttredelsesdato)
//
//            return@map kommuneIdentType(kommuneendring.fylkesnummer.til.single(), tilKlnr) to payload
//        }
//
//    return kommuneIdentType(kommuneendring.fylkesnummer.fra, kommuneendring.kommuneløpenummer.fra) to
//            if (til.size == 1) {
//                // Kun en til-kommuneident. Fra-kommune skal byttes ut med til-kommune, med kommunedata i til.second.
//                // Dvs fra-kommune skal settes utgått, og til-kommune skal opprettes
//                // Brukes også for sammenslåing av kommuner, men sammenslaaing=true,
//                // for at matrikkelen ikke skal opprette samme kommune flere ganger
//                val t = til[0]
//                IdentTransformer.Mapping.Replace(
//                    t.first,
//                    t.second,
//                    kommuneendring.sammenslaaing,
//                )
//            } else {
//                IdentTransformer.Mapping.Split(
//                    listOf(
//                        til[0].first to null, // TODO: Første kommune i input-en blir satt som ny kommune for den utgående kommunen, mulig at dette bør være mer eksplisitt på sikt
//                    ) + til,
//                )
//            }
//}

//fun mapMatrikkelenhetendring(matrikkelenhetendring: Matrikkelenhetendring): NonEmptyList<Pair<Ident, IdentTransformer.Mapping>> {
//
//    val fraGardnummerserieIdent: GardsnummerserieIdent = GardsnummerserieIdent(
//        matrikkelenhetendring.fylkesnummer.fra,
//        matrikkelenhetendring.kommuneløpenummer.fra,
//        matrikkelenhetendring.fraGardsnummer,
//    )
//
//    val tilGardsnummerserie: GardsnummerserieIdent? = if (matrikkelenhetendring.tilGardsnummer != null) {
//        GardsnummerserieIdent(
//            matrikkelenhetendring.fylkesnummer.til,
//            matrikkelenhetendring.kommuneløpenummer.til,
//            matrikkelenhetendring.tilGardsnummer
//        )
//    } else {
//        null
//    }
//
//    val result = ArrayList<Pair<Ident, IdentTransformer.Mapping>>()
//    if (tilGardsnummerserie == null || fraGardnummerserieIdent != tilGardsnummerserie) {
//        val tilAndreGardsnummerserieIdents: MutableSet<GardsnummerserieIdent> = matrikkelenhetendring.bruksnummer
//            .mapTo(HashSet()) { (_, tilGrunneiendomIdent) -> tilGrunneiendomIdent.dropLast() }
//            .apply { if (tilGardsnummerserie != null) add(tilGardsnummerserie) }
//
//        if (tilAndreGardsnummerserieIdents.contains(fraGardnummerserieIdent)) {
//            throw IllegalArgumentException(
//                "Matrikkelendringen er ugyldig, gårdsnummeret bevares for noen bruksnummer, men gårdsnummerserien er " +
//                        "satt til å endres: " +
//                        "$fraGardnummerserieIdent går til $tilGardsnummerserie")
//        }
//
//        if (matrikkelenhetendring.bruksnummer.isEmpty()
//            || tilGardsnummerserie != null
//            && tilAndreGardsnummerserieIdents.size == 1
//            && tilAndreGardsnummerserieIdents.single() == tilGardsnummerserie) {
//            result.add(fraGardnummerserieIdent to IdentTransformer.Mapping.Simple(tilAndreGardsnummerserieIdents.single()))
//        } else {
//            // Vi MÅ bruke split, hvis gårdsnummerserien har flere enn ett element, ellers så vil ikke
//            // (bl.a.?) matrikkelnummerreservasjoner virke. Det betyr at vi må lage parameretere for ALLE bruksnummere for
//            // gårdsnummerserien
//            result.add(fraGardnummerserieIdent to IdentTransformer.Mapping.Split(tilAndreGardsnummerserieIdents.map { it to null }))
//        }
//    }
//
//    for ((fraBruksnummer, tilGrunneiendom) in matrikkelenhetendring.bruksnummer) {
//        result.add(fraGardnummerserieIdent.appendWith(GrunneiendomIdent, fraBruksnummer) to IdentTransformer.Mapping.Simple(tilGrunneiendom))
//    }
//
//    return result.toNonEmptyListOrNull() ?: throw IllegalArgumentException("No mappings generated for matrikkelenhetendring")
//}
//
//suspend fun mapTeig(teigendring: Teigendring): Pair<Ident, IdentTransformer.Mapping> {
//    val teigIdentType = identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, TeigId>()
//
//    return teigIdentType(
//        teigendring.fylkesnummer.fra,
//        teigendring.kommuneløpenummer.fra,
//        teigendring.teigId.fra,
//    ) to
//            IdentTransformer.Mapping.Simple(
//                teigIdentType(
//                    teigendring.fylkesnummer.til,
//                    teigendring.kommuneløpenummer.til,
//                    teigendring.teigId.til,
//                ),
//            )
//}
//
//suspend fun mapVegendring(vegendring: Vegendring): Pair<Ident, IdentTransformer.Mapping> {
//    val adresseparsellIdentType = identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode>()
//
//    val til =
//        vegendring.kommuneløpenummer.til.map { tilKlnr ->
//            val tilAdr =
//                if (vegendring.adressekode.til.size == 1) {
//                    vegendring.adressekode.til.single()
//                } else {
//                    vegendring.adressekode.til[
//                        vegendring.kommuneløpenummer.til.indexOf(
//                            tilKlnr,
//                        ),
//                    ]
//                }
//            adresseparsellIdentType(
//                vegendring.fylkesnummer.til.single(),
//                tilKlnr,
//                tilAdr,
//            ) to null
//        }
//
//    return adresseparsellIdentType(
//        vegendring.fylkesnummer.fra,
//        vegendring.kommuneløpenummer.fra,
//        vegendring.adressekode.fra,
//    ) to
//            if (til.size == 1) {
//                val t = til[0]
//                IdentTransformer.Mapping.Simple(
//                    t.first,
//                    t.second,
//                )
//            } else {
//                IdentTransformer.Mapping.Split(til)
//            }
//}
//
//suspend fun mapVegadresseendring(vegadresseendring: Vegadresseendring): Pair<Ident, IdentTransformer.Mapping> {
//    val vegadresseIdentType = identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode, Adressenummernummer>()
//
//    return vegadresseIdentType(
//        vegadresseendring.fylkesnummer.fra,
//        vegadresseendring.kommuneløpenummer.fra,
//        vegadresseendring.adressekode.fra,
//        vegadresseendring.adressenummer.fra,
//    ) to
//            IdentTransformer.Mapping.Simple(
//                vegadresseIdentType(
//                    vegadresseendring.fylkesnummer.til,
//                    vegadresseendring.kommuneløpenummer.til,
//                    vegadresseendring.adressekode.til,
//                    vegadresseendring.adressenummer.til,
//                ),
//            )
//}
//
//suspend fun mapKretsendring(kretsendring: Kretsendring): Pair<Ident, IdentTransformer.Mapping> {
//    val kretsIdentType = identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Kretstype, Kretsnummer>()
//
//    return kretsIdentType(
//        kretsendring.fylkesnummer.fra,
//        kretsendring.kommuneløpenummer.fra,
//        kretsendring.kretstype.fra,
//        kretsendring.kretsnummer.fra,
//    ) to
//            IdentTransformer.Mapping.Simple(
//                kretsIdentType(
//                    kretsendring.fylkesnummer.til,
//                    kretsendring.kommuneløpenummer.til,
//                    kretsendring.kretstype.til,
//                    kretsendring.kretsnummer.til,
//                ),
//            )
//}

fun <T> Flow<T>.chunked(size: Int): Flow<List<T>> =
    flow {
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
