package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class IdentTransformer(mappings: List<Pair<Ident, Mapping>>) {
    private val kommuneviseRegler: Map<Kommunenummer, List<Pair<Ident, Mapping>>>
    private val andreRegler: List<Pair<Ident, Mapping>>

    init {
        val kRegler = HashMap<Kommunenummer, ArrayList<Pair<Ident, Mapping>>>()
        val aRegler = ArrayList<Pair<Ident, Mapping>>()

        mappings.forEach { m ->
            val sourceType = m.first.type
            m.second.checkIdentType(sourceType)

            val fylkesnummer = m.first.getOrNull<Fylkesnummer>()
            val kommunelopenummer = m.first.getOrNull<Kommunenummer.Lopenummer>()

            if (fylkesnummer != null && kommunelopenummer != null) {
                kRegler.computeIfAbsent(Kommunenummer(fylkesnummer, kommunelopenummer)) { ArrayList() }
                    .add(m)
            } else {
                aRegler.add(m)
            }
        }

        kommuneviseRegler = kRegler
        andreRegler = aRegler
    }

    suspend fun transform(
        entity: Entity,
        idProvider: suspend (IdType<*, *>, Any?) -> Id,
    ): List<Transformation>? {
        val transformIdentMedEvtSammenslaaingsflagg = entity.ident?.transformIdent()
        val primaryTransform = transformIdentMedEvtSammenslaaingsflagg?.first
        val sammenslaaingsflagg = transformIdentMedEvtSammenslaaingsflagg?.second

        val transformedAssociatedIdents = entity.associatedIdents?.flatMap {
            it.transformIdent().first.map { it.first }
        }?.toSet()

        return if (primaryTransform != null) {
            when (primaryTransform.size) {
                0 -> {
                    listOf(
                        Transformation(
                            id = entity.id,
                            sourceEntity = entity,
                            transformedIdent = null,
                            transformedAssociatedIdents = transformedAssociatedIdents,
                            sammenslaaing = sammenslaaingsflagg,
                        ),
                    )
                }

                1 -> {
                    val transform = primaryTransform[0]
                    return if (transform.first != entity.ident || transformedAssociatedIdents != entity.associatedIdents) {
                        listOf(
                            Transformation(
                                id = entity.id,
                                sourceEntity = entity,
                                transformedIdent = transform.first,
                                transformedAssociatedIdents = transformedAssociatedIdents,
                                resultObject = transform.second,
                                sammenslaaing = sammenslaaingsflagg,
                            ),
                        )
                    } else {
                        null
                    }
                }

                else -> {
                    primaryTransform.mapIndexed { index, transform ->
                        val id = if (index == 0) {
                            entity.id
                        } else {
                            idProvider(entity.id.type, transform.first)
                        }
                        Transformation(
                            id = id,
                            sourceEntity = entity,
                            transformedIdent = transform.first,
                            transformedAssociatedIdents = transformedAssociatedIdents,
                            resultObject = transform.second,
                            sammenslaaing = sammenslaaingsflagg,
                        )
                    }
                }
            }
        } else {
            if (entity.ident != null || transformedAssociatedIdents != entity.associatedIdents) {
                listOf(
                    Transformation(
                        id = entity.id,
                        sourceEntity = entity,
                        transformedIdent = null,
                        transformedAssociatedIdents = transformedAssociatedIdents,
                        sammenslaaing = sammenslaaingsflagg,
                    ),
                )
            } else {
                null
            }
        }
    }

    private suspend fun Ident.transformIdent(): Pair<List<Pair<Ident, Payload?>>, Boolean> {
        val fylkesnummer = getOrNull<Fylkesnummer>()
        val kommunelopenummer = getOrNull<Kommunenummer.Lopenummer>()
        val mappings = if (fylkesnummer != null && kommunelopenummer != null) {
            kommuneviseRegler.getOrElse(
                Kommunenummer(
                    fylkesnummer,
                    kommunelopenummer,
                ),
                ::emptyList,
            )
        } else {
            andreRegler
        }

        var sammenslaing = false

        val returmap = mappings.mapNotNull { mapping ->
            val source = mapping.first
            val matches = source.type.types.mapNotNull { componentType ->
                type.bottomTypes[componentType]?.let { it to getOrThrow(it) }
            }
            if (matches.map { it.second } == source.toArray().toList()) {
                mapping to matches
            } else {
                null
            }
        }
            .groupByTo(TreeMap()) { it.second.size }
            .lastEntry()
            ?.let { (_, mappingMatches) ->
                if (mappingMatches.size > 1) {
                    throw RuntimeException("More than one match")
                } else {
                    mappingMatches.singleOrNull() // Kan vel aldri bli null, men samma det...
                }
            }
            ?.let { (mapping, matches) ->
                when (val target = mapping.second) {
                    is Mapping.Simple -> listOf(
                        matches.foldIndexed(this) { targetIndex, transformedIdent, match ->
                            val typeIndex = match.first
                            transformedIdent.updateOrThrow(typeIndex) { target.ident.getOrThrow(targetIndex) }
                        } to target.payload,
                    )

                    is Mapping.Replace -> {
                        val result = matches.foldIndexed(this) { targetIndex, transformedIdent, match ->
                            val typeIndex = match.first
                            transformedIdent.updateOrThrow(typeIndex) { target.ident.getOrThrow(targetIndex) }
                        }

                        if (matches.size == type.size) {
                            // Perfect match: Replace it
                            // Det er kun når det er full match at sammenslåingsflagget videreføres
                            // Altså for fylke eller kommune, og ikke for f.eks matrikkelenhet uten egen parameter
                            sammenslaing = target.sammenslaaing ?: false
                            if (sammenslaing) {
                                // Hvis sammenslåingsflagg er satt, er det for kommune eller fylke nr 2 (eller høyere)
                                // Skal ikke lage opprettingsparameter for kommunen/fylket flere ganger
                                listOf(
                                    result to null,
                                )
                            } else {
                                listOf(
                                    result to null,
                                    result to target.payload,
                                )
                            }
                        } else {
                            // Imperfect match: Transform
                            listOf(
                                result to null,
                            )
                        }
                    }

                    is Mapping.Split -> if (matches.size == type.size) {
                        // Perfect match: Split it
                        target.into.map { (targetIdent, payload) ->
                            if (targetIdent == Ident.Empty) {
                                targetIdent to payload
                            } else {
                                matches.foldIndexed(this) { targetIndex, transformedIdent, match ->
                                    val typeIndex = match.first
                                    transformedIdent.updateOrThrow(typeIndex) { targetIdent.getOrThrow(targetIndex) }
                                } to payload
                            }
                        }
                    } else {
                        // Imperfect match: Unresolved
                        val matchedIndicies = matches.map { it.first }.toSet()
                        val preserveTypes = type.types.mapIndexedNotNull { index, kType ->
                            if (matchedIndicies.contains(index)) {
                                null
                            } else {
                                index to kType
                            }
                        }
                        listOf(
                            if (preserveTypes.isEmpty()) {
                                Ident.Empty to null
                            } else {
                                val newType = identTypeFromKotlinTypes(
                                    preserveTypes.first().second,
                                    *preserveTypes.drop(1).map { it.second }.toTypedArray(),
                                )
                                identWithTypeOrThrow(
                                    newType,
                                    *preserveTypes.map { getOrThrow(it.first) }
                                        .toTypedArray(),
                                ) to null
                            },
                        )
                    }
                }
            } ?: listOf(this to null)

        return Pair(returmap, sammenslaing)
    }

    companion object {
        operator fun invoke(vararg mappings: Pair<Ident, Mapping>) = IdentTransformer(listOf(*mappings))
    }

    sealed interface Mapping {
        fun checkIdentType(identType: IdentOrEmptyType<*>)

        // Simple er de som blir oppdatert med ny kommune (og evt andre identfelt),
        // som vegadresse, krets og teig for mnr mangler
        data class Simple(var ident: Ident, var payload: Payload? = null) : Mapping {
            override fun checkIdentType(identType: IdentOrEmptyType<*>) {
                if (identType != ident.type) {
                    throw IllegalArgumentException("${ident.type} != $identType")
                }
            }
        }

        // Replace er når en kommune erstattes av en annen,
        // som ved fylkessplitting da Ringerike i Viken ble til Ringerike i Buskerud
        // Det er også implementert for fylke.
        // Brukes også for sammenslåing av fylker og kommuner. Når samenslåingsflagget er true,
        // kan samme kommune/fylke forekomme flere ganger som til-kommune/fylke
        data class Replace(var ident: Ident, var payload: Payload? = null, var sammenslaaing: Boolean? = false) : Mapping {
            override fun checkIdentType(identType: IdentOrEmptyType<*>) {
                if (identType != ident.type) {
                    throw IllegalArgumentException("${ident.type} != $identType")
                }
            }
        }

        // Split er når ett fylke splittes i flere (som Viken), eller en kommune i flere kommuner (som Ålesund)
        // Også brukt når bruksnumre under et gårdsnummer splittes til flere gårdsnumre
        // Også brukt når en veg splittes i flere veger
        data class Split(var into: List<Pair<Ident, Payload?>>) : Mapping {
            override fun checkIdentType(identType: IdentOrEmptyType<*>) {
                val type = into.map { it.first.type }
                    .filter { it.size > 0 }
                    .distinct()
                    .single()
                if (identType != type) {
                    throw IllegalArgumentException("$type != $identType")
                }
            }
        }
    }
}
