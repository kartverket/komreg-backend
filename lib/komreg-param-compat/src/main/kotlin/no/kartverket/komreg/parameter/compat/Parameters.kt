package no.kartverket.komreg.parameter.compat

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.mapOrAccumulate
import arrow.core.reduceOrNull
import arrow.core.split
import arrow.core.toNonEmptySetOrNull
import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.core.domain.Bygningsnummer
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.EmptyIdentType
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Ident2
import no.kartverket.komreg.integration.spi.IdentOrEmptyType
import no.kartverket.komreg.integration.spi.IdentType
import no.kartverket.komreg.integration.spi.IdentType1
import no.kartverket.komreg.integration.spi.Payload
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.integration.spi.getOrThrow
import no.kartverket.komreg.integration.spi.identTypeOf1
import no.kartverket.komreg.integration.spi.identTypeOf3
import no.kartverket.komreg.integration.spi.identWithTypeOrThrow
import no.kartverket.komreg.parameter.Adjust
import no.kartverket.komreg.parameter.Create
import no.kartverket.komreg.parameter.CreateOrMerge
import no.kartverket.komreg.parameter.KeyMatch
import no.kartverket.komreg.parameter.Move
import no.kartverket.komreg.parameter.MoveRange
import no.kartverket.komreg.parameter.Parameter
import no.kartverket.komreg.parameter.Recreate
import no.kartverket.komreg.parameter.Split
import no.kartverket.komreg.parameter.data.HList
import no.kartverket.komreg.parameter.data.HList.Snoc
import no.kartverket.komreg.parameter.domain.MatrikkelReceiverFunction
import no.kartverket.komreg.parameter.domain.withMatrikkelTypes
import no.kartverket.komreg.parameter.KeyTransformationLookup
import no.kartverket.komreg.parameter.dsl.ParameterRootDSL
import kotlin.reflect.KType
import kotlin.reflect.cast

@ConsistentCopyVisibility
data class Parameters private constructor(val params: KeyTransformationLookup) : IdentTransformer {

    private val createdIdents = HashSet<Ident>()
    override suspend fun transform(
        entity: Entity,
        idProvider: suspend (IdType<*, *>, Any?) -> Id
    ): List<Transformation> {
        return transformImpl(entity, idProvider)
    }

    private fun transformImpl(
        entity: Entity,
        idProvider: suspend (IdType<*, *>, Any?) -> Id
    ): List<Transformation> {
        val origIdent = entity.ident
        val origKey = origIdent?.toSnoc()
        val origAssocIdents =
            entity.associatedIdents.orEmpty()
                .mapNotNullTo(HashSet()) { ident -> ident.toSnoc()?.let { ident.type to it } }

        val transformedAssocIdents = origAssocIdents.mapNotNullTo(HashSet()) { (identType, key) ->
            when (val result = params[key]) {
                is Either.Left<KeyMatch<*, *>> -> {
                    partialIdent(key.toIdent(identType), result.value.size)
                }
                is Either.Right<KeyMatch<*, *>> -> result.value.updatedKey?.toIdent(identType)
            }
        }.toNonEmptySetOrNull()

        // TODO: SKal det virkelig være sånn som over????
//        val transformedAssocIdents = either {
//            mapOrAccumulate(origAssocIdents) { (identType, key) ->
//                val x = params[key].mapLeft { key to it }.bind()
//                x.updatedKey?.toIdent(identType)
//            }.mapNotNull { it }.toNonEmptySetOrNull()
//        }.getOrElse { errs ->
//            throw IllegalArgumentException("Can not transform associated idents: $errs")
//        }

        if (origKey != null) {
            val match = params[origKey].getOrElse { err ->
                // Dette er ekstremt rart, men hvis transformasjonen feiler,
                // så skal vi returnere en transformasjon med en updated
                // ident med bare den delen av identen som har feilet!
                val updatedIdent = partialIdent(origIdent, err.size)
                return listOf(
                    Transformation(
                        id = entity.id,
                        sourceEntity = entity,
                        transformedIdent = updatedIdent,
                        transformedAssociatedIdents = transformedAssocIdents,
                        resultObject = null
                    )
                )
            }
            return when (match) {
                is KeyMatch.Partial<*, *> -> sequence {
                    yield(
                        Transformation(
                            id = entity.id,
                            sourceEntity = entity,
                            transformedIdent = match.updatedKey.toIdent(origIdent.type),
                            transformedAssociatedIdents = transformedAssocIdents,
                            resultObject = null
                        )
                    )
                }

                is KeyMatch.Perfect<*, *> -> sequence {
                    val (moreTargets, firstTarget) = match.targetsParameters.entries.split()
                        ?: (emptyList<Map.Entry<Snoc<HList, Any>, Parameter.Applied<*, *>>>() to null)
                    val updatedKey = when (val p = match.parameter.parameter) {
                        is Adjust<*, *>, is Move<*, *>, is MoveRange<*, *> -> match.updatedKey
                        is Split<*, *>, is Create<*, *, *> -> firstTarget?.key
                    }

                    for ((k, v) in match.targetsParameters.entries) {
                        when (val p = v.parameter) {
                            is Create<*, *, *> -> {
                                val updatedIdent = k.toIdent(origIdent.type)
                                if (createdIdents.add(origIdent)) {
                                    yield(
                                        Transformation(
                                            id = entity.id,
                                            sourceEntity = entity,
                                            transformedIdent = updatedKey?.toIdent(origIdent.type)
                                                ?: Ident.Empty,
                                            transformedAssociatedIdents = transformedAssocIdents,
                                            resultObject = null
                                        )
                                    )
                                }
                                yield(
                                    Transformation(
                                        id = runBlocking {
                                            idProvider(
                                                entity.id.type,
                                                updatedIdent
                                            )
                                        },
                                        sourceEntity = entity,
                                        transformedIdent = updatedIdent,
                                        transformedAssociatedIdents = transformedAssocIdents,
                                        resultObject = when (p) {
                                            is CreateOrMerge<*, *, *> -> p.`as`.second as Payload
                                            is Recreate<*, *, *> -> p.overriding?.second as? Payload
                                        }
                                    )
                                )
                            }

                            is Split<*, *>, is Move<*, *>, is MoveRange<*, *> -> {
                                val updatedIdent = k.toIdent(origIdent.type)
                                yield(
                                    Transformation(
                                        id = runBlocking {
                                            idProvider(
                                                entity.id.type,
                                                updatedIdent
                                            )
                                        },
                                        sourceEntity = entity,
                                        transformedIdent = updatedIdent,
                                        transformedAssociatedIdents = transformedAssocIdents,
                                        resultObject = null
                                    )
                                )
                            }

                            is Adjust<*, *> -> {}
                        }
                    }
                }

                is KeyMatch.Unmatched<*, *> -> {
                    sequenceOf()
                }
            }.toList()
        }
        return emptyList()
    }

    private fun partialIdent(
        ident: Ident,
        size: Int
    ): Ident {
        return when (val identType = ident.type) {
            EmptyIdentType -> Ident.Empty
            is IdentType<*, *> -> {
                identType.types
                    .subList(size % identType.size, identType.types.size)
                    .reduceOrNull<KType, IdentType<*, *>>(
                        { IdentType(EmptyIdentType, it) }) { acc, type ->
                        IdentType(acc, type)
                    }?.let {
                        val values = ident.toArray().copyOfRange(size, identType.size)
                        identWithTypeOrThrow(it, *values)
                    }
                    ?:Ident.Empty

            }
        }
    }

    companion object {
        private var longBygning: IdentType<Ident2<Fylkesnummer, Kommunenummer.Lopenummer>, Bygningsnummer>
        private var shortBygning: IdentType1<Bygningsnummer>

        init {
            runBlocking {
                longBygning = identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Bygningsnummer>()
                shortBygning = identTypeOf1<Bygningsnummer>()

            }
        }

        private fun Ident.normalize(): Ident {
            return when (this.type) {
                longBygning -> identWithTypeOrThrow(shortBygning, getOrThrow(2))
                else -> return this
            }
        }

        operator fun invoke(block: MatrikkelReceiverFunction<ParameterRootDSL, Unit>): Parameters {
            val dsl = ParameterRootDSL({
                withMatrikkelTypes(this, block)
            })
            val compiled = dsl.flatMap { KeyTransformationLookup(it) }.getOrElse { errs ->
                throw IllegalArgumentException(
                    "Failed to compile parameters${
                        errs.joinToString(
                            "\n    - ",
                            prefix = ":\n    - "
                        )
                    }"
                )
            }

            return Parameters(compiled)
        }

        fun Ident.toHList() = HList.fromArray(this.toArray())
        fun Ident.toSnoc() = when (val hlist = this.toHList()) {
            HList.Empty -> null
            is Snoc<*, *> -> hlist
        }

        fun Snoc<*, *>.toIdent(identType: IdentOrEmptyType<*>): Ident {
            require(identType is IdentType<*, *>)
            return identWithTypeOrThrow(
                identType,
                *this.toArray().map { Comparable::class.cast(it) }.toTypedArray()
            )
        }
    }
}