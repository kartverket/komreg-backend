package no.kartverket.komreg.parameter

import arrow.core.EitherNel
import arrow.core.getOrElse
import arrow.core.raise.either
import no.kartverket.komreg.parameter.data.DomainType
import no.kartverket.komreg.parameter.data.HList
import no.kartverket.komreg.parameter.intermediate.Intermediate
import no.kartverket.komreg.parameter.intermediate.associateByForwardKeys
import no.kartverket.komreg.parameter.intermediate.combineAll

@JvmInline
value class ParameterCollection private constructor(
    private val wrapped: List<Parameter.Applied<Parameter<HList.Empty, *>, *>>
) : Collection<Parameter.Applied<Parameter<HList.Empty, *>, *>> by wrapped {


    companion object {
        operator fun invoke(parametersBuilder: Iterable<Intermediate.Applied<*, *>>): EitherNel<Intermediate.Error, ParameterCollection> =
            either {
                val intermediatesCombined = combineAll(parametersBuilder).bind()
                val combinedByForwardMapping =
                    associateByForwardKeys(intermediatesCombined).getOrElse { errs ->
                        throw AssertionError(
                            """When combining intermediate parameters by forward key (only), the intermediate parameters 
                                failed to combine, even after being combined by bidirectional key. 
                                This should not be possible, and surly is a bug.""".trimIndent() + errs.joinToString(
                                "\n\t - ",
                                prefix = "Errors reported: \n\t - "
                            )
                        )
                    }

                val types =
                    intermediatesCombined.associate { it.type.finalType.javaClass to it.type }

                val combinedByForwardMappingComplete = combinedByForwardMapping
                    .entries
                    .asSequence()
                    .mapNotNull { (key, v) ->
                        when (val parentKey = key.init) {
                            is HList.Snoc<*, *> -> parentKey to v.fileLocations
                            is HList.Empty -> null

                        }
                    }
                    .groupBy({ it.first }, { it.second })
                    .asSequence()
                    .map { (k, v) -> k to v.asSequence().flatMap { it.asSequence() }.toHashSet() }
                    .map { (keep, fileLocations) ->
                        val p = Intermediate.Keep(keep)
                        keep to Intermediate.Applied(
                            p,
                            (types[keep.last::class.java]!!) as DomainType<Any>,
                            fileLocations
                        )
                    }
                    .fold(HashMap(combinedByForwardMapping)) { acc, (k, v) ->
                        acc.apply { putIfAbsent(k, v) }
                    }

                val parameters =
                    combinedByForwardMappingComplete.mapValuesTo(HashMap()) { (k, iap) ->
                        val param =
                            iap.intermediate.toParameter() as Parameter<HList.Snoc<HList, Any>, Any>
                        Parameter.Applied(
                            param,
                            iap.type as DomainType<Any>,
                            iap.fileLocations
                        )
                    }

                for ((key, ap) in parameters.entries.sortedByDescending { it.key.size }) {
                    val parentKey = when (val init = key.init) {
                        HList.Empty -> continue
                        is HList.Snoc<*, *> -> init
                    }
                    parameters.remove(key)
                    parameters.compute(parentKey) { parentKey, parentAp: Parameter.Applied<Parameter<HList.Snoc<HList, Any>, Any>, Any>? ->
                        when (val pParent = parentAp!!.parameter) {
                            is Adjust<*, *> -> {
                                val by: MutableList<Parameter.Applied<Parameter<HList.Snoc<HList.Snoc<HList, Any>, Any>, *>, *>> =
                                    ArrayList(pParent.by.size + 1)
                                by.addAll(pParent.by as List<Parameter.Applied<Parameter<HList.Snoc<HList.Snoc<HList, Any>, Any>, *>, *>>)
                                by.add(ap as Parameter.Applied<Parameter<HList.Snoc<HList.Snoc<HList, Any>, Any>, *>, *>)
                                Parameter.Applied<Parameter<HList.Snoc<HList, Any>, Any>, Any>(
                                    Adjust(pParent.keep, by),
                                    parentAp.type as DomainType<Any>,
                                    parentAp.fileLocation
                                )
                            }

                            is Move<*, *> -> TODO("raise error")
                            is MoveRange<*, *> -> TODO("raise error")
                            is Split<*, *> -> {
                                val by: MutableList<Parameter.Applied<Parameter.Invalidating<HList.Snoc<HList.Snoc<HList, Any>, Any>, *>, *>> =
                                    ArrayList(pParent.by.size + 1)
                                by.addAll(pParent.by as List<Parameter.Applied<Parameter.Invalidating<HList.Snoc<HList.Snoc<HList, Any>, Any>, *>, *>>)
                                by.add(ap as Parameter.Applied<Parameter.Invalidating<HList.Snoc<HList.Snoc<HList, Any>, Any>, *>, *>)
                                Parameter.Applied<Parameter<HList.Snoc<HList, Any>, Any>, Any>(
                                    Split(pParent.from, by),
                                    parentAp.type,
                                    parentAp.fileLocation
                                )
                            }

                            is CreateOrMerge<*, *, *> -> {
                                val createOrMerge = pParent as CreateOrMerge<HList.Snoc<HList, Any>, Any, Any>
                                val also: MutableList<Parameter.Applied<Create<HList.Snoc<HList.Snoc<HList, Any>, Any>, *, *>, *>> =
                                    ArrayList(pParent.also.size + 1)
                                also.addAll(createOrMerge.also)
                                also.add(ap as Parameter.Applied<Create<HList.Snoc<HList.Snoc<HList, Any>, Any>, *, *>, *>)
                                Parameter.Applied(createOrMerge.copy(also = also), parentAp.type, parentAp.fileLocation)
                            }
                            is Recreate<*, *, *> -> TODO("raise error")
                        }
                    }
                }

                ParameterCollection(parameters.values.toList() as List<Parameter.Applied<Parameter<HList.Empty, *>, *>>)

            }
    }
}