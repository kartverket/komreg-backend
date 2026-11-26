package no.kartverket.komreg.transformation.reform

import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.IdentOrEmptyType
import no.kartverket.komreg.integration.spi.IdentType
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.integration.spi.identTypeFromKotlinTypes
import no.kartverket.komreg.integration.spi.identWithTypeOrThrow
import no.kartverket.komreg.transformation.reform.ReformOp.Move
import no.kartverket.komreg.transformation.reform.ReformOp.Retire
import no.kartverket.komreg.transformation.reform.ReformOp.RetireTo
import kotlin.reflect.KType

/**
 * Lager [Transformation]-er direkte fra validerte [ReformClaims].
 *
 * Hovedidenten og de assosierte identene slås opp med [ReformClaims.lookup] (mest spesifikke regel langs
 * forelderkjeden). [ReformOp.Derive] gir en ny entitet per eksakt treff på [ReformOp.Derive.from], og
 * [ReformOp.Create] er ikke støttet enda.
 *
 */
class ReformClaimsTransformer(
    private val claims: ReformClaims
) {
    init {
        require(claims.creates.isEmpty()) {
            "Kun Derives er støttet foreløpig, Creates er ikke støttet (enda)${claims.creates.joinToString("\n\t-", ":\n\t-")}"
        }
    }
    /** Transformasjonene for én eksisterende entitet. Tom liste hvis ingenting endres. */
    suspend fun transform(entity: Entity, idFor: suspend (IdType<*, *>, Any?) -> Id): List<Transformation> {
        val ident = entity.ident
        val transformedIdent = ident?.let { transformKey(it) }
        val transformedAssociatedIdents = entity.associatedIdents
            ?.mapNotNullTo(LinkedHashSet()) { transformKey(it) }
        val derives = (ident as? Ident.NonEmpty)?.let { claims.derives[it] }.orEmpty()

        val sourceChanged = transformedIdent != ident || transformedAssociatedIdents != entity.associatedIdents

        return buildList {
            if (sourceChanged) {
                add(
                    Transformation(
                        id = entity.id,
                        sourceEntity = entity,
                        transformedIdent = transformedIdent,
                        transformedAssociatedIdents = transformedAssociatedIdents,
                        resultObject = null,
                        sammenslaaing = null,
                    ),
                )
            }
            derives.forEach { d ->
                add(
                    Transformation(
                        id = idFor(entity.id.type, d.target),
                        sourceEntity = entity,
                        transformedIdent = d.target,
                        transformedAssociatedIdents = transformedAssociatedIdents,
                        resultObject = d.payload,
                        sammenslaaing = null,
                    ),
                )
            }
        }
    }

    /** Ny nøkkel for [key] etter reformen. `null` betyr at nøkkelen utgår uten etterfølger. */
    private suspend fun transformKey(key: Ident): Ident? {
        if (key !is Ident.NonEmpty) return key
        val match = claims.lookup(key) ?: return key
        return when (val op = match.operation) {
            is Move -> replacePrefix(key, match, op.target)
            is RetireTo -> replacePrefix(key, match, op.target)
            is Retire -> if (match.isExact) op.successor else unmatchedPart(key, match)
        }
    }

    /** Bytter ut de [ReformMatch.matchedSize] første komponentene i [key] med [target]. */
    private suspend fun replacePrefix(key: Ident.NonEmpty, match: ReformMatch, target: Ident.NonEmpty): Ident {
        if (match.isExact) return target
        val values = target.toArray() + key.toArray().drop(match.matchedSize)
        val type = if (target.type === key.type.prefixOfSize(match.matchedSize)) {
            key.type as IdentType<*, *>
        } else {
            identTypeOf(target.type.types + key.type.types.drop(match.matchedSize))
        }
        return identWithTypeOrThrow(type, *values)
    }

    /** Delvis nøkkel: bare komponentene under treffet. Treffet er på en forelder, så den er aldri tom. */
    private suspend fun unmatchedPart(key: Ident.NonEmpty, match: ReformMatch): Ident {
        val type = identTypeOf(key.type.types.drop(match.matchedSize))
        return identWithTypeOrThrow(type, *key.toArray().drop(match.matchedSize).toTypedArray())
    }
}

private suspend fun identTypeOf(types: List<KType>): IdentType<*, *> =
    identTypeFromKotlinTypes(types.first(), *types.drop(1).toTypedArray())

private fun IdentOrEmptyType<*>.prefixOfSize(size: Int): IdentOrEmptyType<*> {
    var type: IdentOrEmptyType<*> = this
    while (type.size > size) type = (type as IdentType<*, *>).prefix
    return type
}
