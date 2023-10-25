package no.kartverket.komreg.transformation

import no.kartverket.komreg.integration.spi.*
import java.util.TreeMap

class IdentTransformer(vararg mappings: Pair<Ident, Ident?>) {
    private val map = mapOf(
        *(
            mappings.onEach { mapping ->
                val sourceType = mapping.first.type
                val targetType = mapping.second?.type
                if (targetType != null && targetType != sourceType) {
                    throw IllegalArgumentException("$targetType != $sourceType")
                }
            }
            ),
    )

    suspend fun transform(
        entity: Entity,
        idGeneratorManager: IdGeneratorManager,
    ): List<Transformation>? {
        val transformedIdent = entity.ident?.transformIdent()
        val transformedIdents = entity.associatedIdents?.map { it.transformIdent() }?.toSet()

        return if (transformedIdent != entity.ident || transformedIdents != entity.associatedIdents) {
            listOf(
                Transformation(
                    id = entity.id,
                    sourceEntity = entity,
                    transformedIdent = transformedIdent,
                    transformedAssociatedIdents = transformedIdents,
                ),
            )
        } else {
            null
        }
    }

    private suspend fun Ident.transformIdent(): Ident {
        return map.map { mapping ->
            val source = mapping.key
            val matches = source.type.types.mapNotNull { componentType ->
                type.bottomTypes[componentType]?.let { it to getOrThrow(it) }
            }
            if (matches.map { it.second } == source.toArray().toList()) {
                mapping to matches
            } else {
                null
            }
        }
            .filterNotNull()
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
                val target = mapping.value
                if (target == null) {
                    val matchedIndicies = matches.map { it.first }.toSet()
                    val preserveTypes = type.types.mapIndexedNotNull { index, kType ->
                        if (matchedIndicies.contains(index)) {
                            null
                        } else {
                            index to kType
                        }
                    }
                    if (preserveTypes.isEmpty()) {
                        Ident.Empty
                    } else {
                        val newType = identTypeFromKotlinTypes(
                            preserveTypes.first().second,
                            *preserveTypes.drop(1).map { it.second }.toTypedArray(),
                        )
                        identWithTypeOrThrow(
                            newType,
                            *preserveTypes.map { getOrThrow(it.first) }
                                .toTypedArray(),
                        )
                    }
                } else {
                    matches.foldIndexed(this) { targetIndex, transformedIdent, match ->
                        val typeIndex = match.first
                        transformedIdent.updateOrThrow(typeIndex) { target.getOrThrow(targetIndex) }
                    }
                }
            } ?: this
    }
}
