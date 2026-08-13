package no.kartverket.komreg.parameter.intermediate

import no.kartverket.komreg.parameter.*
import no.kartverket.komreg.parameter.data.DomainType
import no.kartverket.komreg.parameter.data.Enumerator
import no.kartverket.komreg.parameter.data.FileLocation
import no.kartverket.komreg.parameter.data.HList
import no.kartverket.komreg.parameter.data.HList.Snoc
import no.kartverket.komreg.parameter.data.KeyCombinableType

sealed interface Intermediate<KI : HList, KL : Any> {
    data class Keep<KI : HList, KL : Any>(val key: Snoc<KI, KL>) : Intermediate<KI, KL> {
        override fun toParameter(): Adjust<KI, KL> = Adjust(key.last, arrayListOf())
    }

    data class Split<KI : HList, KL : Any>(val from: Snoc<KI, KL>) : Intermediate<KI, KL> {
        override fun toParameter(): no.kartverket.komreg.parameter.Split<KI, KL> {
            return Split(
                from.last,
                emptyList()
            )
        }
    }
    data class Move<KI : HList, KL : Any>(
        val from: Snoc<KI, KL>,
        val to: Snoc<KI, KL>
    ) : Intermediate<KI, KL> {
        override fun toParameter(): no.kartverket.komreg.parameter.Move<KI, KL> {
            return Move(from.last, to)
        }
    }

    data class CreateOrMerge<KI : HList, KL : Any, Data : Any>(
        val to: Snoc<KI, KL>,
        val from: Set<Snoc<KI, KL>>,
        val `as`: Pair<KeyCombinableType<KL, Data>, Data>
    ) : Intermediate<KI, KL> {
        override fun toParameter(): no.kartverket.komreg.parameter.CreateOrMerge<KI, KL, Data> {
            return CreateOrMerge(to.last, from, `as`, emptyList())
        }
    }

    data class Recreate<KI : HList, KL : Any, Data : Any>(
        val to: Snoc<KI, KL>,
        val from: Snoc<KI, KL>,
        val `as`: Pair<KeyCombinableType<KL, Data>, Data>?
    ) : Intermediate<KI, KL> {
        override fun toParameter(): no.kartverket.komreg.parameter.Recreate<KI, KL, Data> {
            return Recreate(to.last, from, `as`, emptyList())
        }
    }

    data class MoveRange<KI : HList, KL : Any>(
        val fromStart: Snoc<KI, KL>,
        val count: Int,
        val toStart: Snoc<KI, KL>,
        val enumerator: Enumerator<KL>
    ) : Intermediate<KI, KL> {
        override fun toParameter(): Parameter<KI, KL> {
            return MoveRange(fromStart.last, count, toStart, enumerator)
        }
    }

    data class Applied<KI : HList, KL : Any>(
        val intermediate: Intermediate<KI, KL>,
        val type: DomainType<KL>,
        val fileLocations: Set<FileLocation>
    )

    sealed interface Error {
        data class DivergingKey<KL : Any>(val a: Intermediate<*, KL>, val b: Intermediate<*, KL>) : Error
        data class DivergingKeys<KI : HList, KL : Any>(val a: Intermediate<KI, KL>, val b: Intermediate<KI, KL>) : Error
        data class Conflict<KI : HList, KL : Any>(
            val key: Snoc<KI, KL>,
            val first: Applied<KI, KL>,
            val second: Applied<KI, KL>,
            val more: List<Applied<KI, KL>> = emptyList()
        ) : Error
        object CreateDataError : Error
    }

    fun toParameter() : Parameter<KI, KL>
}

