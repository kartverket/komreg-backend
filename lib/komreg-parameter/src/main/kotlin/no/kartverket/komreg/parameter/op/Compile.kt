@file:OptIn(ExperimentalRaiseAccumulateApi::class)

package no.kartverket.komreg.parameter.op

import arrow.core.*
import arrow.core.raise.ExperimentalRaiseAccumulateApi
import arrow.core.raise.accumulate
import arrow.core.raise.either
import no.kartverket.komreg.parameter.data.Tuple
import no.kartverket.komreg.parameter.data.Tuple.Ap
import no.kartverket.komreg.parameter.data.append
import no.kartverket.komreg.parameter.data.length
import no.kartverket.komreg.parameter.op.SubOp.Adjust
import no.kartverket.komreg.parameter.op.SubOp.Create
//import no.kartverket.komreg.parameter.op.SubOp.Expire
import no.kartverket.komreg.parameter.op.SubOp.Move
import no.kartverket.komreg.parameter.op.SubOp.MoveRange
import no.kartverket.komreg.parameter.op.SubOp.Split
import kotlin.sequences.flatten

sealed interface CompileError
data class SourceConflict<A>(
    val first: LoOp.SourceOp<A>,
    val second: LoOp.SourceOp<A>,
    val more: Set<LoOp.SourceOp<A>>
) : CompileError

data class TargetConflict<A>(
    val first: LoOp.TargetOp<A>,
    val second: LoOp.TargetOp<A>,
    val more: Set<LoOp.TargetOp<A>>
) : CompileError

data class SourceTargetConflict<A>(
    val sourceOps: NonEmptyList<LoOp.SourceOp<A>>,
    val targetOps: NonEmptyList<LoOp.TargetOp<A>>,
) : CompileError


data class InputError(val input: String, val codeLocations: NonEmptyList<CodeLocation>) : CompileError


data class LoOpProgram private constructor(
    val sourceOpMap: Map<Ap<*, *>, LoOp.SourceOp<Ap<*, *>>>,
    val sourceOpTargetMap: Map<Ap<*, *>, Set<LoOp.TargetOp<Ap<*, *>>>>,
    val targetOpMap: Map<Ap<*, *>, LoOp.TargetOp<Ap<*, *>>>,
) {
    companion object {
        fun compile(
            compilables: Collection<Compilable<Ap<out Tuple.Empty, *>>>
        ): EitherNel<CompileError, LoOpProgram> = either {
            val subOps: MutableCollection<SubOp<Tuple.Empty, *>> = arrayListOf()
            val mergeOps: MutableCollection<Merge<*, *>> = arrayListOf()
            for (hiOp in compilables) {
                when (hiOp) {
                    is SubOp<Tuple.Empty, *> -> subOps.add(hiOp)
                    is Merge<*, *> -> mergeOps.add(hiOp)
                }
            }
            val (sourceOpMap,targetOpMap) = compile(subOps, mergeOps).bind()

            val sourceTargetOpsMap: MutableMap<Ap<*, *>, MutableSet<LoOp.TargetOp<Ap<*, *>>>> = HashMap(sourceOpMap.size / 3 * 4)
            for (sourceOp in sourceOpMap.values.asSequence()) {
                val foobar = when (sourceOp) {
                    is LoOp.Expire<Ap<*, *>> -> {
                        sourceOp
                            .to
                            .map {
                                targetOpMap[it] ?: LoOp.Keep(it, sourceOp.to.toNonEmptySetOrThrow(), LoOp.Cause.ImplicitUpdate(nonEmptySetOf(sourceOp)))
                            }
                    }
                    is LoOp.SourceOp.WithSingleTarget<Ap<*, *>> -> {
                        listOf(requireNotNull(targetOpMap[sourceOp.to]) {
                            "(BUG) Should not be possible, all source ops should have a target op by this point"
                        })
                    }
                }
                sourceTargetOpsMap.computeIfAbsent(sourceOp.from) { HashSet(foobar.size) }.addAll(foobar)
            }

            LoOpProgram(sourceOpMap, sourceTargetOpsMap, targetOpMap)
        }
    }
}

fun LoOpProgram.Companion.compile(vararg compilables: Compilable<Ap<out Tuple.Empty, *>>) = compile(compilables.toList())
fun LoOpProgram.Companion.compile(vararg compilables: EitherNel<CompileError, Compilable<Ap<out Tuple.Empty, *>>>) = either {
    accumulate {
        compile(compilables.mapNotNull { nullIfError(it) }).bindNel()
    }
}

fun LoOpProgram.Companion.compileOrThrow(vararg compilables: EitherNel<CompileError, Compilable<Ap<out Tuple.Empty, *>>>): LoOpProgram {
    return compile(*compilables).getOrElse { errs ->
        throw IllegalArgumentException("Compilation failed: ${errs.joinToString("\n   - ", "\n   - ")}")
    }
}


fun LoOpProgram.toList() : List<LoOp<Ap<*, *>>> {
    val sequence = HashSet<LoOp<Ap<*, *>>>(sourceOpMap.size + targetOpMap.size).run {
        addAll(sourceOpMap.values)
        addAll(targetOpMap.values)
        ArrayList<LoOp<Ap<*, *>>>(size).apply { addAll(this@run) }
    }

    sequence.sortBy {
        when (it) {
            is LoOp.Create<Tuple> -> -1073741824 + it.to.length
            is LoOp.Move<Tuple> -> ((256 - it.from.length) shl 1) or 0
            is LoOp.MoveChildrenAndExpire<Tuple> -> ((256 - it.from.length) shl 1) or 1
            is LoOp.Expire<Tuple> -> ((256 - it.from.length) shl 1) or 2
            is LoOp.Keep<Tuple> -> 1073741824 - it.to.length
        }
    }

    return sequence
}



private fun compile(
    subOps: Collection<SubOp<Tuple.Empty, *>>,
    mergeOps: Collection<Merge<*, *>>
): EitherNel<CompileError, Pair<Map<Ap<*,*>, LoOp.SourceOp<Ap<*, *>>>, Map<Ap<*,*>, LoOp.TargetOp<Ap<*, *>>>>> =
    either {
        accumulate {
            // Først, gjør om alle Merge-opersjoner til SubOps, og grupper dem etter til prefix, sånn at
            // de kan lagt til som underoparsjoner av SubOps som har samme til-prefix
            val mergeOpsByPrefix: MutableMap<Tuple, out Collection<SubOp<*, *>>> = mergeOps
                .mapNotNull {
                    nullIfError(it.getTargetSupOpPairs())
                }
                .flatten()
                .groupByTo(HashMap(), { it.first }) { it.second }

            val compiledOps: MutableList<LoOp<Ap<*,*>>> = ArrayList();

            // Legg til operasjoner fra Merge-opsjoner som ikke er lagt kan gjøres om til SubOps, og må
            // gjøre om til LoOps direkte
            for (merge in mergeOps) {
                val (mergeSourceOps, targetOp) = merge.getLoOps()
                compiledOps.add(targetOp)
                for (sourceOp in mergeSourceOps) {
                    compiledOps.add(sourceOp)
                }
            }

            // Kompiler alle SubOps til LoOps
            subOps
                .mapOrAccumulate { subOp -> subOp.compile(Tuple.Empty, mergeOpsByPrefix).bind() }
                .forEach { compiledOps += it }


            // Kompiler alle MergeOps som kan gjøres om til SubOps, og ikke er lagt til via andre
            // SubOps - og legg til i listen over kompilerte operasjoner
            mergeOpsByPrefix.forEach { (prefix, mergeOps) ->
                mergeOps.forEach { subOp ->
                    subOp.compile(prefix, mergeOpsByPrefix).bind().forEach { compiledOps += it }
                }
            }

            // Partisjoner i SourceOps og TargetOps for validering
            val sourceOps = HashMap<Ap<*, *>, MutableList<LoOp.SourceOp<Ap<*, *>>>>()
            val targetOps = HashMap<Ap<*, *>, MutableList<LoOp.TargetOp<Ap<*, *>>>>()
            for (compiledOp in compiledOps) {
                if (compiledOp is  LoOp.SourceOp<Ap<*, *>>) {
                    sourceOps.computeIfAbsent(compiledOp.from, { mutableListOf() }).add(compiledOp)
                }
                if (compiledOp is  LoOp.TargetOp<Ap<*, *>>) {
                    targetOps.computeIfAbsent(compiledOp.to, { mutableListOf() }).add(compiledOp)
                }
            }

            // Legg til implisitte updates for source ops som ikke har en tilsvarende
            // target op
            for (sourceOp in sourceOps.values.asSequence().flatten()) {
                when (sourceOp) {
                    is LoOp.Expire<Ap<*, *>> -> {
//                        for (to in sourceOp.to) {
//                            targetOps.compute(to) { to, ops ->
//                                if (ops == null) {
//                                    mutableListOf(LoOp.Keep(to, nonEmptySetOf(sourceOp.from), LoOp.Cause.ImplicitUpdate(nonEmptySetOf(sourceOp))))
//                                } else if (ops.none { to == it.to }) {
//                                    ops.apply {
//                                        add(LoOp.Keep(to, nonEmptySetOf(sourceOp.from), LoOp.Cause.ImplicitUpdate(nonEmptySetOf(sourceOp))))
//                                    }
//                                } else {
//                                    ops
//                                }
//                            }
//                        }
                    }
                    is LoOp.SourceOp.WithSingleTarget<Ap<*, *>> -> {
                        targetOps.compute(sourceOp.to) { to, ops ->
                            if (ops == null) {
                                mutableListOf(LoOp.Keep(sourceOp.to, nonEmptySetOf(sourceOp.from), LoOp.Cause.ImplicitUpdate(nonEmptySetOf(sourceOp))))
                            } else if (ops.none { to == it.to }) {
                                ops.apply {
                                    add(LoOp.Keep(sourceOp.to, nonEmptySetOf(sourceOp.from), LoOp.Cause.ImplicitUpdate(nonEmptySetOf(sourceOp))))
                                }
                            } else {
                                ops
                            }
                        }
                    }
                }
            }

            // Sjekk at ingen operasjoner har samme fra verdi
            for (sourceOps in sourceOps.values) {
                accumulate(
                    SourceConflict(
                        sourceOps.getOrNull(0) ?: continue,
                        sourceOps.getOrNull(1) ?: continue,
                        sourceOps.drop(2).toHashSet()
                    )
                )
            }
            // Sjekk at ingen operasjoner har samme til verdi
            for (targetOps in targetOps.values) {

                accumulate(
                    TargetConflict(
                        targetOps.getOrNull(0) ?: continue,
                        targetOps.getOrNull(1) ?: continue,
                        targetOps.drop(2).toHashSet()
                    )
                )
            }

            // Sjekk at verdi har både en operasjon med denne som fra-verdi og
            // en operasjon med denne verdien som til-verdi
            for (key in sourceOps.keys.intersect(targetOps.keys)) {
                val targetOps = targetOps[key]
                    ?.toNonEmptyListOrNull()
                    ?: continue
                val sourceOps = sourceOps[key]
                    ?.toNonEmptyListOrNull()
                    ?: continue
                accumulate(
                    SourceTargetConflict(sourceOps, targetOps)
                )

            }

            // Sjekk at ingen overliggende verdi for en TargetOp (beholdes) har
            // en SourceOp (utgår)
            for ((x, _) in targetOps) {
                var init = x.init
                while (init != Tuple.Empty) {
                    val curInit = init
                    init = init.init ?: Tuple.Empty
                    val sourceOpsConflict = sourceOps[curInit]?.toNonEmptyListOrNull() ?: continue
                    val targetOpsConflict = targetOps[x]?.toNonEmptyListOrNull() ?: continue
                    accumulate(
                        SourceTargetConflict(
                            sourceOps = sourceOpsConflict,
                            targetOps = targetOpsConflict
                        )
                    )
                }
            }



            // Gjør om de midlertidige listene av operasjoner til maps med unike fra- og til-verdier
            // (de er midliertigie for å tillate duplikate til verdier så vi kan samle opp alle
            // feil uten å feile med en gang)
            val resultSourceOps = sourceOps
                .mapValuesNotNull { (_, ops) ->
                    ops.distinct().singleOrNull()
                }
            val resultTargetOps = targetOps
                .mapValuesNotNull { (_, ops) ->
                    ops.distinct().singleOrNull()
                }
            resultSourceOps to resultTargetOps
        }
    }

private fun <Init : Tuple, Last> SubOp<Init, Last>.compile(
    prefix: Init,
    mergeSubOpMap: MutableMap<Tuple, out Collection<SubOp<*, *>>>
): EitherNel<CompileError, List<LoOp<Ap<*,*>>>> = either {
    when (val op = this@compile) {
        is SubOp.AdjustOrSplit<Init, Last> -> {
            val subOpPrefix = prefix.append(op.from)
            @Suppress("UNCHECKED_CAST")
            val mergeOps =
                mergeSubOpMap.remove(subOpPrefix) as? Collection<SubOp<Ap<Init, Last>, *>>
                    ?: emptyList()
            (op.ops + mergeOps)
                .map { subOp ->
                    subOp.compile(subOpPrefix, mergeSubOpMap)
                }
                .bindAll()
                .flatten()
                .plusElement(when(op) {
                    is Adjust<Init, Last> ->
                        LoOp.Keep(subOpPrefix, nonEmptySetOf(subOpPrefix), LoOp.Cause.SiblingOp(op))
                    is Split<Init, Last> -> {
                        val tos = op.to.mapTo(HashSet()) { prefix.append(it) }
                        LoOp.Expire(tos, subOpPrefix, LoOp.Cause.SiblingOp(op))
                    }
                })
        }

        is Move<Init, Last> -> {
//            val sourceOp =
//            val targetOp = when (op.`as`) {
//                is Move.As.New<Last> -> LoOp.Create(op.to, hashSetOf(prefix.append(op.from)), op.`as`.data, LoOp.Cause.SiblingOp(op))
//                Move.As.Same -> LoOp.Update(op.to, nonEmptySetOf(prefix.append(op.from)), LoOp.Cause.SiblingOp(op))
//            }
            listOf(
                LoOp.Move(op.to, prefix.append(op.from), LoOp.Cause.SiblingOp(op)),
            )
        }

        is MoveRange<Init, Last> -> compileMoveRange(op, prefix).bind()
        is Create<Last> -> listOf(
                LoOp.Create(prefix.append(op.to), emptySet(), op.data, LoOp.Cause.SiblingOp(op))
            )

    }
}

private fun <Init : Tuple, Last> compileMoveRange(op: MoveRange<Init, Last>, prefix: Init): EitherNel<CompileError, List<LoOp<Ap<*,*>>>> {
    return either {
        generateSequence(op.from to op.to) { (from, to) ->
            if (op.partialNext.compare(from, op.fromEnd) >= 0) return@generateSequence null
            val nextFrom = op.partialNext.next(from)
                ?: throw IllegalStateException("partialNext should be able to generate the next value")
            val nextToLast = op.partialNext.next(to.last) ?: raise(
                InputError(
                    "partialNext should be able to generate the next value of $to",
                    op.cause.codeLocations
                ).nel()
            )
            Pair(nextFrom, to.init.append(nextToLast))
        }.flatMap { (from, to) ->
            sequenceOf(
                LoOp.Move(to, prefix.append(from), LoOp.Cause.SiblingOp(op)),
            )
        }.toList()
    }
}