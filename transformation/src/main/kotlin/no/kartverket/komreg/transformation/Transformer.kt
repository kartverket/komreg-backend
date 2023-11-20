package no.kartverket.komreg.transformation

import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.*

interface Storage {
    fun writeTransformationsToDatabase(kjoringId: Int, transformResultList: List<Transformation>)

    fun readTransformationsFromDatabase(kjoringId: Int): Flow<Transformation>

    fun createTilbakeføringsstatusForKjoring(kjoringId: Int, entitySinks: List<EntitySink>)

    fun setTilbakeføringsStatusForSink(sink: EntitySink, status: String, kjoringId: Int, erOppretting: Boolean)
    fun hentIkkeStartedeTilbakeføringerForNyeEntiteter(kjoringId: Int): List<String>

    fun hentIkkeStartedeTilbakeføringerForErstattendeEntiteter(kjoringId: Int): List<String>

    fun setStatusForKjøring(kjoringId: Int, status: String)
}

suspend fun transform(
    kjoringId: Int,
    input: Reguleringsinput,
    entitySources: List<EntitySource>,
    entityProcessors: List<EntityProcessor>,
    entitySinks: List<EntitySink>,
    idGeneratorManager: IdGeneratorManager,
    kommuneService: KommuneService,
    storage: Storage,
    skalTilbakefores: Boolean,

) {
    val transformer = IdentTransformer(mapInput(input))

    val gjenværendeSinkerForNyeEntiteter = storage.hentIkkeStartedeTilbakeføringerForNyeEntiteter(kjoringId)
    val gjenværendeSinkerForErstattendeEntiteter =
        storage.hentIkkeStartedeTilbakeføringerForErstattendeEntiteter(kjoringId)

    if (gjenværendeSinkerForNyeEntiteter.isNotEmpty() && gjenværendeSinkerForErstattendeEntiteter.isNotEmpty()) {
        // TODO: Fjern når Fylkeendring får FraEnTilMange
        if (input.fylker.isNotEmpty()) {
            val fylkeFlow = createFylker(input, kommuneService)

            storage.writeTransformationsToDatabase(kjoringId, fylkeFlow.toList())
        }

        entitySources.forEach { entitySource ->
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
        }
    }

    entityProcessors.forEach { processor ->
        storage.readTransformationsFromDatabase(kjoringId)
            .collect { processor.consume(it) }
        val result = processor.produce()
        storage.writeTransformationsToDatabase(kjoringId, result.toList())
    }

    val transformations = storage.readTransformationsFromDatabase(kjoringId)

    if (skalTilbakefores) {
        // Kjør ut alle nyopprettinger
        storage.setStatusForKjøring(kjoringId, "STARTET_TILBAKEFØRING")

        entitySinks.forEach { sink ->

            if (gjenværendeSinkerForNyeEntiteter.contains(sink.id)) {
                try {
                    sink.consumeTransformations(
                        transformations.filter {
                            val sourceEntity = it.sourceEntity
                            sourceEntity == null || sourceEntity.id != it.id
                        },
                        input.ikrafttredelsesdato.toJavaLocalDate(),

                    )
                    storage.setTilbakeføringsStatusForSink(sink, "FERDIG", kjoringId, erOppretting = true)
                } catch (e: Exception) {
                    storage.setTilbakeføringsStatusForSink(sink, "FEILET", kjoringId, erOppretting = true)
                    storage.setStatusForKjøring(kjoringId, "TILBAKEFØRING_FEILET")
                    throw e
                }
            }
        }

        // Kjør ut resten
        // TODO: Hva med "slettinger"
        entitySinks.forEach { sink ->
            if (gjenværendeSinkerForErstattendeEntiteter.contains(sink.id)) {
                try {
                    sink.consumeTransformations(
                        transformations.filter {
                            val sourceEntity = it.sourceEntity
                            sourceEntity != null && sourceEntity.id == it.id
                        },
                        input.ikrafttredelsesdato.toJavaLocalDate(),
                    )
                    storage.setTilbakeføringsStatusForSink(sink, "FERDIG", kjoringId, erOppretting = false)
                } catch (e: Exception) {
                    storage.setTilbakeføringsStatusForSink(sink, "FEILET", kjoringId, erOppretting = false)
                    storage.setStatusForKjøring(kjoringId, "TILBAKEFØRING_FEILET")
                    return
                }
            }
        }

        storage.setStatusForKjøring(kjoringId, "FULLFØRT_TILBAKEFØRING")
    } else {
        transformations.collect()
        storage.setStatusForKjøring(kjoringId, "IKKE_TILBAKEFØRT")
    }
}

private suspend fun mapInput(input: Reguleringsinput): List<Pair<Ident, IdentTransformer.Mapping>> {
    val kommuneMap = input.kommuner.associateBy { it.kommunenummer }

    return input.endringer.map { m ->
        when (m) {
            is Fylkeendring -> TODO("Venter på at Fylkeendring får FraEnTilMange")
            is Kommuneendring -> mapKommuneendring(m, kommuneMap, input.ikrafttredelsesdato)
            is Kretsendring -> mapKretsendring(m)
            is Matrikkelenhetendring -> mapMatrikkelenhetendring(m)
            is Teigendring -> mapTeig(m)
            is Vegadresseendring -> mapVegadresseendring(m)
            is Vegendring -> mapVegendring(m)
        }
    }
}

private suspend fun mapKommuneendring(
    kommuneendring: Kommuneendring,
    kommuneMap: Map<Kommunenummer, Kommune>,
    ikrafttredelsesdato: LocalDate,
): Pair<Ident, IdentTransformer.Mapping> {
    val kommuneIdentType: IdentType2<Fylkesnummer, Kommunenummer.Lopenummer> = identTypeOf2()

    val til = kommuneendring.kommuneløpenummer.til.map { tilKlnr ->
        val nyttKommunenummer = Kommunenummer(kommuneendring.fylkesnummer.til, tilKlnr)
        val kommune = kommuneMap.getValue(nyttKommunenummer)

        val payload = kommune.tilKommunedata(ikrafttredelsesdato)

        return@map kommuneIdentType(kommuneendring.fylkesnummer.til, tilKlnr) to payload
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
            vegendring.fylkesnummer.til,
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

private suspend fun createFylker(
    input: Reguleringsinput,
    kommuneService: KommuneService,
): Flow<Transformation> {
    val fylkeIdentType = identTypeOf1<Fylkesnummer>()

    return flow {
        input.fylker.forEach { fylke ->
            emit(
                Transformation(
                    id = kommuneService.idForFylke(fylke.fylkesnummer),
                    sourceEntity = null,
                    transformedIdent = fylkeIdentType(fylke.fylkesnummer),
                    resultObject = Fylkesdata(fylke.fylkesnavn.name),
                ),
            )
        }
    }
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
