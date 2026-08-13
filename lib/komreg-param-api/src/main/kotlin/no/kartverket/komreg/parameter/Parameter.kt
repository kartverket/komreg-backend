package no.kartverket.komreg.parameter

import no.kartverket.komreg.parameter.Parameter.Applied
import no.kartverket.komreg.parameter.Parameter.Invalidating
import no.kartverket.komreg.parameter.data.*
import no.kartverket.komreg.parameter.data.HList.Snoc
import kotlin.math.abs

sealed interface Parameter<KI : HList, KL : Any> {
    sealed interface Invalidating<KI : HList, KL : Any> : Parameter<KI, KL>
    sealed interface ToOne<KI : HList, KL : Any> : Parameter<KI, KL>

    data class Applied<out P : Parameter<*, KL>, KL : Any>(
        val parameter: P,
        val type: DomainType<KL>,
        val fileLocation: Set<FileLocation>
    )

//    interface Error {
//        data class Conflict<KI : HList, KL : Any>(
//            val a: Applied<Parameter<KI, KL>, KL>,
//            val b: Applied<Parameter<KI, KL>, KL>
//        ) : Error {
//            init {
//                val foo = 0
//            }
//
//            fun asDivergingKeys() = DivergingKeys(a, b)
//        }

//        data class DivergingKeys<KI : HList, KL : Any>(
//            val a: Applied<Parameter<KI, KL>, KL>,
//            val b: Applied<Parameter<KI, KL>, KL>
//        ) : Error {
//            init {
//                val foo = 0
//            }
//
////            fun asConflict() = Conflict(a, b)
//        }

//        data class CreationDataConflict<KI : HList, KL : Any>(
//            val a: Applied<Create<KI, KL, *>, KL>,
//            val b: Applied<Create<KI, KL, *>, KL>,
//            val message: String
//        ) : Error
//
//        object InvalidParent : Error
//        data class DisappearingTarget<KII : HList, KIL : Any, KL : Any>(
//            val source: Applied<Parameter<Snoc<KII, KIL>, KL>, KL>,
//            val target: Applied<Parameter<KII, KIL>, KIL>
//        ) : Error
//
//        data class IntermediateError(val err: Intermediate.Error) : Error
//    }
}

//fun Parameter.Applied<Parameter<HList.Empty, *>, *>.associateByForwardMapping(
//): Sequence<Pair<Snoc<HList.Empty, *>, Applied<Parameter<HList.Empty, *>, *>>> =
//    when (val p = this.parameter) {
//        is Adjust<HList.Empty, *> -> sequenceOf(HList * p.keep to this)
//        is CreateOrMerge<*, *, *> -> sequenceOf(HList * p.to to this)
//        is Recreate<*, *, *> -> sequenceOf(HList * p.to to this)
//        is Move<*, *> -> sequenceOf(HList * p.from to this)
//        is Split<*, *> -> sequenceOf(HList * p.from to this)
//        is MoveRange<*, *> -> p.keySequence { fromLast, _, _ ->
//            yield(HList * fromLast to this@associateByForwardMapping)
//        }
//    }

//internal fun <KI : HList, KL : Any> Applied<Parameter<KI, in KL>, in KL>.withParameter(parameter: Parameter<KI, KL>) : Applied<Parameter<KI, KL>, KL> {
//    TODO()
//}
//
//internal fun <KI : HList, KL : Any, Data : Any> Split.ToNew<KI, KL, Data>.toCreate(init: KI, split: Split<KI, KL>): CreateOrMerge<KI, KL, Data> {
//    return CreateOrMerge(to.last, emptySet(), `as`, also)
//}
//
//
//internal fun <P : Parameter<*, KL>, Q : P, KL : Any> Q.appliedBy(applied: Applied<P, KL>, vararg moreApplied: Applied<P, KL> ): Applied<Q, KL> {
//    val fileLocations = moreApplied.fold(HashSet(applied.fileLocation)) { fileLocations, additional ->
//        check(applied.type == additional.type) {
//            "Applied.type not equal: ${applied.type} != ${additional.type}"
//        }
//        fileLocations.addAll(additional.fileLocation)
//        fileLocations
//    }
//    return Applied(this, applied.type, fileLocations)
//}

internal fun <KI : HList, KL : Any, R> MoveRange<KI, KL>.keySequence(
    block: suspend (SequenceScope<R>).(fromLast: KL, toInit: KI, toLast: KL) -> Unit
): Sequence<R> {
    val next = if (count >= 0) {
        enumerator::next
    } else {
        enumerator::prev
    }
    val toInit = toStart.init
    return sequence {
        var fromLast = fromStart
        var toLast = toStart.last
        var n = abs(count)
        while (n > 0) {
            block(fromLast, toInit, toLast)
            fromLast = next(fromLast) ?: break
            toLast = next(toLast) ?: break
            n--
        }
    }
}

data class Move<KI : HList, KL : Any>(
    val from: KL,
    val to: Snoc<KI, KL>
) : Invalidating<KI, KL>, Parameter.ToOne<KI, KL>

data class MoveRange<KI : HList, KL : Any>(
    val fromStart: KL,
    val count: Int,
    val toStart: Snoc<KI, KL>,
    val enumerator: Enumerator<KL>,
) : Invalidating<KI, KL>, Parameter.ToOne<KI, KL>

data class Split<KI : HList, KL : Any>(
    val from: KL,
    val by: List<Applied<Invalidating<Snoc<KI, KL>, *>, *>>
) : Invalidating<KI, KL> {
    override fun toString(): String {
        return "Split(from=$from, by=[${by.size} elements...])"
    }

}

data class Adjust<KI : HList, KL : Any>(
    val keep: KL,
    val by: List<Applied<Parameter<Snoc<KI, KL>, *>, *>>
) : Parameter<KI, KL>, Parameter.ToOne<KI, KL> {
    override fun toString(): String {
        return "Adjust(keep=$keep, by=[${by.size} elements...]))"
    }
}

sealed interface Create<KI : HList, KL : Any, Data : Any> : Parameter.ToOne<KI, KL> {
    val to: KL
    val also: List<Applied<Create<Snoc<KI, KL>, *, *>, *>>
}

data class Recreate<KI : HList, KL : Any, Data : Any>(
    override val to: KL,
    val from: Snoc<KI, KL>,
    val overriding: Pair<KeyCombinableType<KL, Data>, Data>?,
    override val also: List<Applied<Create<Snoc<KI, KL>, *, *>, *>>
) : Create<KI, KL, Data>

data class CreateOrMerge<KI : HList, KL : Any, Data : Any>(
    override val to: KL,
    val from: Set<Snoc<KI, KL>>,
    val `as`: Pair<KeyCombinableType<KL, Data>, Data>,
    override val also: List<Applied<Create<Snoc<KI, KL>, *, *>, *>>
) : Create<KI, KL, Data>