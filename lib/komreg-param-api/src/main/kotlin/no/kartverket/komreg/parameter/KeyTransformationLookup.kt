package no.kartverket.komreg.parameter

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.raise.either
import no.kartverket.komreg.parameter.Parameter.Applied
import no.kartverket.komreg.parameter.Parameter.Invalidating
import no.kartverket.komreg.parameter.data.HList
import no.kartverket.komreg.parameter.data.HList.Snoc
import no.kartverket.komreg.parameter.data.times
import no.kartverket.komreg.parameter.intermediate.Intermediate

@ConsistentCopyVisibility
data class KeyTransformationLookup private constructor(
    private val fromMap: Map<Snoc<*, *>, Mapping<*, *>>,
    private val toMap: Map<Snoc<*, *>, Mapping<*, *>>
) {
    private data class Mapping<KI : HList, KL : Any>(
        val key: Set<Snoc<KI, KL>>,
        val parameter: Applied<Parameter<KI, KL>, KL>
    )

    operator fun <KI : HList, KL : Any> get(key: Snoc<KI, KL>): Either<KeyMatch<*, *>, KeyMatch<KI, KL>> = either {
        val result = fromMap[key] as? Mapping<KI, KL>
        if (result != null) {
            val targetsParameters = result.key
                .mapNotNull { toKey ->
                    (toMap[toKey] as? Mapping<KI, KL>)?.let { mapping -> toKey to mapping.parameter }
                }
                .toMap()
            KeyMatch.Perfect(result.key.singleOrNull(), result.parameter, targetsParameters, key.size)
        } else {
            when (val keyInit = key.init) {
                HList.Empty -> KeyMatch.Unmatched(key)
                is Snoc<*, *> -> {
                    val parentMatch = get(keyInit).bind()
                    when (parentMatch) {
                        is KeyMatch.Matched<*, *> -> {
                            val parentUpdated = parentMatch.updatedKey as? KI
                            if (parentUpdated != null) {
                                KeyMatch.Partial(parentUpdated * key.last, parentMatch, key.size)
                            } else {
                                raise(parentMatch)
                            }
                        }

                        is KeyMatch.Unmatched<*, *> -> {
                            val parentUpdated = parentMatch.updatedKey as KI
                            KeyMatch.Unmatched(parentUpdated * key.last)
                        }
                    }
                }
            }
        }
    }

    companion object {
        operator fun invoke(
            parameters: ParameterCollection
        ): EitherNel<Intermediate.Error, KeyTransformationLookup> = either {

            val biDirMappings = parameters
                .asSequence()
                .flatMap { it.toBiDirMappings(HList.Empty) }
                .toList()

            val fromMap = biDirMappings
                .asSequence()
                .mapNotNull { it.from?.let { from -> Triple(from, it.to, it.applied) } }
                .groupingBy { it.first }
                .foldTo(
                    HashMap(),
                    { _, (_, toKey, ap) -> (toKey?.let { hashSetOf(it) } ?: hashSetOf()) to ap })
                { _, acc, (_, toKey, ap) ->
                    if (toKey != null) {
                        acc.first.add(toKey)
                    }
                    check(acc.second == ap)
                    acc
                }
                .mapValues { (_, pair) ->
                    check(pair.first.isNotEmpty())
                    Mapping(pair.first, pair.second as Applied<Parameter<HList, Any>, Any>)
                }

            val toMap = biDirMappings
                .asSequence()
                .mapNotNull {
//                    if (it.applied.parameter is Split) {
                        null
//                    } else {
                        it.to?.let { to -> Triple(to, it.from, it.applied) }
//                    }
                }
                .groupingBy { it.first }
                .foldTo(
                    HashMap(),
                    { _, (_, from, ap) -> (from?.let { hashSetOf(it) } ?: hashSetOf()) to ap })
                { k, (aFrom, aAp), (bTo, bFrom, bAp) ->
                    val x = aAp as Applied<Parameter<HList, Any>, Any>
                    val y = bAp as Applied<Parameter<HList, Any>, Any>
                    val ap = x.retardedCombine(y, k.init)
                    if (bFrom != null) {
                        aFrom.add(bFrom)
                    }
                    aFrom to ap
                }
                .mapValues { (_, pair) ->
                    Mapping(pair.first, pair.second as Applied<Parameter<HList, Any>, Any>)
                }



            KeyTransformationLookup(fromMap, toMap)
        }

        private fun <KI : HList, KL : Any> Applied<Parameter<KI, KL>, KL>.retardedCombine(
            that: Applied<Parameter<KI, KL>, KL>,
            init: KI,
        ) : Applied<Parameter<KI, KL>, KL> {
            return if (this == that) {
                this
            } else when (val paramThis = this.parameter) {
                is CreateOrMerge<KI, KL, *> -> when (val paramThat = that.parameter) {
                    is CreateOrMerge<KI, KL, *> -> {
                        check(parameter.to == that.parameter.to)
                        Applied(paramThis.copy(also = paramThis.also + paramThat.also), type, fileLocation + that.fileLocation)
                    }
                    is Split<KI, KL> -> {
                        val splitSto= paramThat.splitsTo(init).toSet()
                        Applied(paramThis.copy(from = paramThis.from + splitSto), type, fileLocation + that.fileLocation)
                    }
                    else -> throw IllegalArgumentException()
                }

                is Split<KI, KL> -> when (val paramThat = that.parameter) {
                    is CreateOrMerge<KI, KL, *> -> that.retardedCombine(this, init)
                    is Split<KI, KL> -> {
                        check(parameter.from == that.parameter.from)
                        Applied(paramThis.copy(by = paramThis.by + paramThat.by), type, fileLocation + that.fileLocation)
                    }
                    else -> throw IllegalArgumentException()
                }
                else ->throw IllegalArgumentException()
            }

        }

        private data class BiDirMapping<KI : HList, KL : Any>(
            val from: Snoc<KI, KL>?,
            val to: Snoc<KI, KL>?,
            val applied: Applied<Parameter<KI, KL>, KL>
        )

        private fun <KI : HList, KL : Any> Invalidating<KI, KL>.splitsTo(init: KI): Sequence<Snoc<KI, KL>> {
            return when (this) {
                is Move<KI, KL> -> sequenceOf(to)
                is MoveRange<KI, KL> -> keySequence { fromLast, toInit, toLast ->
                    yield(toInit * toLast)
                }

                is Split<KI, KL> -> {
                    by.asSequence().flatMap { ap ->
                        ap.parameter.splitsTo(init * from).map { it.init }
                    }.distinct()
                }
            }
        }

        private fun <KI : HList, KL : Any> Applied<Parameter<KI, out KL>, out KL>.toBiDirMappings(
            init: KI
        ): Sequence<BiDirMapping<*, *>> {
            val self = this as Applied<Parameter<KI, KL>, KL>
            return when (val p = this.parameter) {
                is Adjust<KI, KL> -> {
                    p.by.asSequence().flatMap {
                        it.toBiDirMappings(init * p.keep)
                    } + BiDirMapping(init * p.keep, init * p.keep, self)
                }

                is CreateOrMerge<KI, KL, *> ->
                    if (p.from.isEmpty()) {
                        sequenceOf(BiDirMapping(null, init * p.to, self))
                    } else p.from.asSequence().map { from ->
                        BiDirMapping(from, init * p.to, this)
                    } + p.also.flatMap { it.toBiDirMappings(init * p.to) }

                is Recreate<KI, KL, *> -> {
                    sequenceOf(BiDirMapping(p.from, init * p.to, self))
                }

                is Move<KI, KL> -> {
                    sequenceOf(BiDirMapping(init * p.from, p.to, self))
                }

                is MoveRange<KI, KL> -> {
                    p.keySequence { fromLast, toInit, toLast ->
                        yield(BiDirMapping(init * fromLast, toInit * toLast, self))
                    }
                }

                is Split<KI, KL> -> {
                    p.by.asSequence().flatMap {
                        it.toBiDirMappings(init * p.from)
                    } + p.splitsTo(init).map {
                        BiDirMapping(init * p.from, it, self)
                    }
                }
            }
        }
    }
}

