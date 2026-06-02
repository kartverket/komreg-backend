package no.kartverket.komreg.transformation

import arrow.core.EitherNel
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType
import no.kartverket.komreg.integration.spi.*
import no.kartverket.komreg.parameter.data.Tuple
import no.kartverket.komreg.parameter.data.Tuple.Ap
import no.kartverket.komreg.parameter.data.append
import no.kartverket.komreg.parameter.data.toIdent
import no.kartverket.komreg.parameter.data.toTuple
import no.kartverket.komreg.parameter.op.Compilable
import no.kartverket.komreg.parameter.op.CompileError
import no.kartverket.komreg.parameter.op.LoOp
import no.kartverket.komreg.parameter.op.LoOpProgram
import no.kartverket.komreg.parameter.op.compileOrThrow

class IdentTransformer(private val loOps: LoOpProgram) {
    suspend fun transform(
        entity: Entity,
        idProvider: suspend (IdType<*, *>, Any?) -> Id,
    ): List<Transformation> {
        val originalPath = entity.ident?.toTuple()
        val transformedIdent = originalPath?.let { transformPartialPath(it) }?.toIdent()
        val transformedAssociatedIdents = entity.associatedIdents?.mapTo(HashSet()) {
            requireNotNull(transformPath(it.toTuple())?.toIdent()) { "Associated ident ${it} could not be transformed" }
        }

        val createTransforms = if (originalPath != null) {
            validateNotIsTarget(originalPath)
            loOps
                .sourceOpTargetMap
                .getOrDefault(originalPath, emptySet())
                .mapNotNull { targetOp ->
                    when (targetOp) {
                        is LoOp.Create<Ap<*, *>> -> {
                            Transformation(
                                id = idProvider(entity.id.type, transformedIdent),
                                sourceEntity = null,
                                transformedIdent = targetOp.to.toIdent(),
                                transformedAssociatedIdents = transformedAssociatedIdents,
                                resultObject = targetOp.data,
                            )
                        }

                        is LoOp.Keep<*> -> null
                        is LoOp.Move<*> -> null
                    }
                }


        } else {
            emptyList()
        }

        return if (entity.ident != transformedIdent || entity.associatedIdents != transformedAssociatedIdents) {
            listOf(
                Transformation(
                    id = entity.id,
                    sourceEntity = entity,
                    transformedIdent = transformedIdent,
                    transformedAssociatedIdents = transformedAssociatedIdents,
                    resultObject = null,
                )
            ) + createTransforms
        } else {
            createTransforms
        }
    }

    private fun validateNotIsTarget(identAsTuple: Tuple) {
        when (val rule = loOps.targetOpMap[identAsTuple]) {
            is LoOp.Create<*>, is LoOp.Move<*> -> throw IllegalArgumentException(
                """
                  Create should not have a to-value of an already existing entity. Found rule $rule for ident: $identAsTuple
                """.trimIndent()
            )

            is LoOp.Keep<*>, null -> {}
        }
    }

    fun <Init : Tuple, Last> transformPath(path: Ap<Init, Last>): Ap<Init, Last>? {
        @Suppress("UNCHECKED_CAST")
        val sourceOp = loOps.sourceOpMap[path] as? LoOp.SourceOp<Ap<Init, Last>>
        return if (sourceOp == null) {
            when (val init = path.init) {
                is Tuple.Empty -> path
                is Ap<*, *> -> {
                    @Suppress("UNCHECKED_CAST") val transformedInit = transformPath(init) as? Init
                    transformedInit?.append(path.last)
                }
            }
        } else when (sourceOp) {
            is LoOp.Expire<Ap<Init, Last>> -> {
                @Suppress("UNCHECKED_CAST")
                loOps.sourceOpTargetMap[path]
                    ?.map { it.to }
                    ?.distinct()
                    ?.singleOrNull() as? Ap<Init, Last>
            }
            is LoOp.Move<Ap<Init, Last>> -> sourceOp.to
            is LoOp.MoveChildrenAndExpire<Ap<Init, Last>> -> sourceOp.to
        }
    }

    fun transformPath(path: Tuple): Tuple? = when (path) {
        is Tuple.Empty -> path
        is Ap<*, *> -> transformPath(path)
    }


    fun <Init : Tuple, Last> transformPartialPath(path: Ap<Init, Last>): Tuple {
        @Suppress("UNCHECKED_CAST")
        val sourceOp = loOps.sourceOpMap[path] as? LoOp.SourceOp<Ap<Init, Last>>

        return when (sourceOp) {
            is LoOp.Expire<Ap<Init, Last>> -> Tuple.Empty
            is LoOp.Move<Ap<Init,Last>> -> sourceOp.to
            is LoOp.MoveChildrenAndExpire<Ap<Init, Last>> -> sourceOp.to
            null -> transformPartialPath(path.init).append(path.last)

        }
    }

    fun transformPartialPath(path: Tuple): Tuple = when (path) {
        is Tuple.Empty -> path
        is Ap<*, *> -> transformPartialPath(path)
    }

    companion object
}

operator fun IdentTransformer.Companion.invoke(vararg compilables: EitherNel<CompileError, Compilable<Ap<out Tuple.Empty, *>>>): IdentTransformer =
    IdentTransformer(
        LoOpProgram.compileOrThrow(*compilables)
    )
