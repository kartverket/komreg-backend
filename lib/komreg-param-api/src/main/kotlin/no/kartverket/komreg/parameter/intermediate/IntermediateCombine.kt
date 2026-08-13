package no.kartverket.komreg.parameter.intermediate

import arrow.core.EitherNel
import arrow.core.NonEmptyList
import arrow.core.nel
import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.either
import arrow.core.raise.mapValuesOrAccumulate
import no.kartverket.komreg.parameter.data.CombinableType
import no.kartverket.komreg.parameter.data.FileLocation
import no.kartverket.komreg.parameter.data.HList
import no.kartverket.komreg.parameter.data.HList.Snoc
import no.kartverket.komreg.parameter.data.KeyCombinableType
import no.kartverket.komreg.parameter.data.times
import kotlin.math.abs

internal fun combineAll(
    intermediates: Iterable<Intermediate.Applied<*, *>>
): EitherNel<Intermediate.Error, Collection<Intermediate.Applied<*, *>>> =
    either {
        val grouped = intermediates
            .asSequence()
            .flatMap { it.associateByBidirectionalKeys() }
            .groupBy({ it.first }, { it.second })
        val combined = mapValuesOrAccumulate(grouped) { (_, v) ->
            v.reduce { a, b ->
                a.combine(b).bind()
            }
        }
        combined.values
    }

internal fun associateByForwardKeys(
    values: Iterable<Intermediate.Applied<*, *>>
): EitherNel<Intermediate.Error, Map<Snoc<*, *>, Intermediate.Applied<*, *>>> = either {
    val grouped = values
        .flatMap { it.associateByForwardKeys() }
        .groupByTo(HashMap(), { it.first }, { it.second })
    mapValuesOrAccumulate(grouped) { (_, v) ->
        v.reduce { acc, applied ->
            acc.combine(applied).bind()
        }
    }
}

internal fun Intermediate.Applied<*, *>.associateByForwardKeys(
): Sequence<Pair<HList.Snoc<*, *>, Intermediate.Applied<*, *>>> =
    when (val p = this.intermediate) {
        is Intermediate.Keep<*, *> -> sequenceOf(p.key to this)
        is Intermediate.CreateOrMerge<*, *, *> -> sequenceOf(p.to to this)
        is Intermediate.Move<*, *> -> sequenceOf(p.from to this)
        is Intermediate.MoveRange<*, *> -> {
            p.keySequence { from, to ->
                yield(from to this@associateByForwardKeys)
            }
        }

        is Intermediate.Recreate<*, *, *> -> sequenceOf(p.to to this)
        is Intermediate.Split<*, *> -> sequenceOf(p.from to this)
    }

private fun Intermediate.Applied<*, *>.associateByBidirectionalKeys(
): Sequence<Pair<Snoc<*, *>?, Intermediate.Applied<*, *>>> =
    when (val p = this.intermediate) {
        is Intermediate.Keep<*, *> -> sequenceOf(p.key to this)
        is Intermediate.CreateOrMerge<*, *, *> -> p.from.asSequence().map { it to this } + (p.to to this)
        is Intermediate.Move<*, *> -> sequenceOf(p.from to this, p.to to this)
        is Intermediate.MoveRange<*, *> -> p.keySequence { from, to ->
            yield(from to this@associateByBidirectionalKeys)
            yield(to to this@associateByBidirectionalKeys)
        }

        is Intermediate.Recreate<*, *, *> -> sequenceOf(p.from to this, p.to to this)
        is Intermediate.Split<*, *> -> sequenceOf(p.from to this)
    }

//private fun Intermediate.Applied<*, *>.associateByBidirectionalKeys(
//): Sequence<Pair<UnorderedPair<Snoc<*, *>>, Intermediate.Applied<*, *>>> =
//    when (val p = this.intermediate) {
//        is Intermediate.Keep<*, *> -> {
//            sequenceOf(UnorderedPair(p.key, p.key) to this)
//        }
//
//        is Intermediate.Merge<*, *, *> -> {
//            p.from.asSequence().map { UnorderedPair(it, p.to) to this }
//        }
//
//        is Intermediate.Move<*, *> -> {
//            sequenceOf(UnorderedPair(p.from, p.to) to this)
//        }
//
//        is Intermediate.MoveRange<*, *> -> {
//            p.keySequence { from, to ->
//                yield(UnorderedPair(from, to) to this@associateByBidirectionalKeys)
//            }
//        }
//
//        is Intermediate.Recreate<*, *, *> -> {
//            sequenceOf(UnorderedPair(p.from, p.to) to this)
//        }
//
//        is Intermediate.Split<*, *> -> {
//            sequenceOf(UnorderedPair(p.from, p.from) to this)
//        }
//    }

private fun Intermediate.Applied<*, *>.combine(
    other: Intermediate.Applied<*, *>
): EitherNel<Intermediate.Error, Intermediate.Applied<*, *>> =
    either {
        val self = this@combine
        require(self.type == other.type) {
            "Diverging types"
        }
        val selfCast = self as Intermediate.Applied<HList, Any>
        val thatCast = other as Intermediate.Applied<HList, Any>
        selfCast.combine(thatCast).bind()
    }

@JvmName("combineTyped")
private fun <KL : Any> Intermediate.Applied<HList, KL>.combine(
    other: Intermediate.Applied<HList, KL>
): EitherNel<Intermediate.Error, Intermediate.Applied<*, KL>> =
    either {
        val intermediate1 =
            this@combine.intermediate.combine(other.intermediate).bind() as Intermediate<HList, KL>

        // TODO: Keep errors from applied
        Intermediate.Applied<HList, KL>(
            intermediate1,
            type,
            hashSetOf<FileLocation>().apply { addAll(fileLocations); addAll(other.fileLocations) })
    }

@JvmName("combineIntermediate")
private fun <KL : Any> Intermediate<HList, KL>.combine(
    other: Intermediate<HList, KL>
): EitherNel<Intermediate.Error, Intermediate<*, KL>> =
    either {
        val self = this@combine
        when (self) {
            is Intermediate.Keep<*, KL> -> {
                when (other) {
                    is Intermediate.Keep<*, KL> -> {
                        ensure(self.key == other.key) {
                            Intermediate.Error.DivergingKey(self, other).nel()
                        }
                        self
                    }

                    else -> TODO()
                }
            }

            is Intermediate.CreateOrMerge<HList, KL, *> -> {
                when (other) {
                    is Intermediate.CreateOrMerge<HList, KL, *> -> {
                        ensure(self.to == other.to) {
                            Intermediate.Error.DivergingKey(self, other).nel()
                        }
                        val combinedData = combineCreationData(
                            self.`as`, other.`as`
                        ) as Pair<KeyCombinableType<KL, Any>, Any>
                        Intermediate.CreateOrMerge(self.to, self.from + other.from, combinedData)
                    }

                    else -> TODO()
                }
            }

            is Intermediate.Move<HList, KL> -> {
                when (other) {
                    is Intermediate.Move<HList, KL> -> {
                        ensure(self.from == other.from && self.to == other.to) {
                            Intermediate.Error.DivergingKey(self, other).nel()
                        }
                        self
                    }

                    else -> TODO()
                }
            }

            is Intermediate.MoveRange<*, KL> -> {
                TODO()
            }

            is Intermediate.Recreate<HList, KL, *> -> {
                when (other) {
                    is Intermediate.Recreate<HList, KL, *> -> {
                        ensure(self.to == other.to && self.from == other.from) {
                            Intermediate.Error.DivergingKey(self, other).nel()
                        }
                        val combinedData = combineCreationData(
                            self.`as`, other.`as`
                        ) as Pair<KeyCombinableType<KL, Any>, Any>
                        Intermediate.Recreate(self.to, self.from, combinedData)
                    }

                    else -> TODO()
                }
            }

            is Intermediate.Split<HList, KL> -> {
                when (other) {
                    is Intermediate.Split<HList, KL> -> {
                        ensure(self.from == other.from) {
                            Intermediate.Error.DivergingKey(self, other).nel()
                        }
                        self
                    }

                    else -> TODO()
                }
            }
        }
    }

private fun <KL : Any> Raise<NonEmptyList<Intermediate.Error>>.combineCreationData(
    aData: Pair<KeyCombinableType<KL, *>, *>?,
    bData: Pair<KeyCombinableType<KL, *>, *>?
): Pair<KeyCombinableType<KL, *>, *>? {
    if (aData == null) {
        return bData
    } else if (bData == null) {
        return aData
    }
    val (aType, a) = aData
    val (bType, b) = bData
    ensure(aType == bType) {
        Intermediate.Error.CreateDataError.nel()
    }
    return if (a == null) {
        bData
    } else if (b == null) {
        aData
    } else {
        @Suppress("UNCHECKED_CAST") val valueType = aType.valueType as CombinableType<Any>
        val x = valueType.combine(a, b).mapLeft {
            Intermediate.Error.CreateDataError.nel()
        }.bind()
        aType to x
    }
}

private fun <KI : HList, KL : Any, R> Intermediate.MoveRange<KI, KL>.keySequence(
    block: suspend (SequenceScope<R>).(from: Snoc<KI, KL>, to: Snoc<KI, KL>) -> Unit
): Sequence<R> {
    val next = if (count >= 0) {
        enumerator::next
    } else {
        enumerator::prev
    }
    val fromInit = fromStart.init
    val toInit = toStart.init
    return sequence {
        var fromLast = fromStart.last
        var toLast = toStart.last
        var n = abs(count)
        while (n > 0) {
            block(fromInit * fromLast, toInit * toLast)
            fromLast = next(fromLast) ?: break
            toLast = next(toLast) ?: break
            n--
        }
    }
}