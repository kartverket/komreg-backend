@file:OptIn(ExperimentalRaiseAccumulateApi::class)

package no.kartverket.komreg.parameter

import arrow.core.raise.ExperimentalRaiseAccumulateApi
import arrow.core.raise.accumulate
import arrow.core.raise.context.bind
import arrow.core.raise.either
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.core.domain.TypedPayload
import no.kartverket.komreg.core.domain.Fylkesnummer as Fylke
import no.kartverket.komreg.core.domain.Kommunenummer.Lopenummer as Kommune
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer as Gard

import no.kartverket.komreg.parameter.data.*
import no.kartverket.komreg.parameter.op.SubOp.*
import no.kartverket.komreg.parameter.op.*
import no.kartverket.komreg.core.domain.Matrikkelnummer.Bruksnummer as Bruk

operator fun <Init : Tuple, Last : Comparable<Last>> Init.div(last: Last): Tuple.Ap<Init, Last> = HList(this, last)
operator fun <Last : Comparable<Last>> Last.unaryPlus(): Tuple.Ap<Tuple.Empty, Last> = HList(Tuple.Empty, this)

fun main() {
    val params = listOf(
        Merge(
            setOf(
                +Fylke(1) / Kommune(1),
                +Fylke(1) / Kommune(2),
                +Fylke(3) / Kommune(1),
            ),
            Merge.ToNew(+Fylke(1) / Kommune(10), object : TypedPayload<Kommune> {}),
            listOf(
                Merge.Move(+Fylke(3) / Kommune(1) / Gard(10), Gard(11)),
                Merge.Split(+Fylke(3) / Kommune(1) / Gard(20), listOf(
                    Move(Bruk(10), +Gard(15) / Bruk(22)).getOrNull()!!
                ))
            )
        ),
        Adjust(Fylke(1), listOf(
            Adjust(Kommune(3), listOf(
                Move(Gard(2), +Fylke(1) / Kommune(99) / Gard(12))
            )),
            Adjust(Kommune(5), listOf(
                Move(Gard(2), +Fylke(1) / Kommune(98) / Gard(12)),
                Move(Gard(3), +Fylke(1) / Kommune(10) / Gard(12))
            )),
            Split(Kommune(4), listOf(
                Move(Gard(2), +Fylke(1) / Kommune(11) / Gard( 13)),
                Move(Gard(3), +Fylke(2) / Kommune(12) / Gard( 14))
            )),
            Create(Kommune(98), object : TypedPayload<Kommune> {}),
        )),
    )

    val x = either {
        accumulate {
            val params = params
                .mapNotNull { nullIfError(it) }
            LoOpProgram.compile(params).bindNel()
        }
    }

    x.fold(
        { errs -> errs.forEach { println("Error: ${it}") } },
        { ops -> ops.toList().forEach { println(it) } }
    )
}


//val params2 = listOf(
//    Merge(
//        setOf(
//            +Fylke(51) / Kommune(3),
//            +Fylke(5) / Kommune(34),
//        ),
//        LoOp.Create(+Fylke(32) / Kommune(4), emptyMap()),
//        listOf(
//            Merge.Move(+Kommune(34) / Gard(10), Gard(3)),
////                Merge.Move(+Kommune(34) / "Gard(3)", "Gard(3)"),
//            Merge.Split(+ Kommune(3) / Gard(3), listOf(
//                Move(Bruk(3), +Kommune(4) / Gard(3) / Bruk(4)).getOrNull()!!
//            )
//            )
//        )),
//    Move(Fylke(1), +Fylke(20)),
//    Adjust(Fylke(2), listOf(
//        Move(Kommune(3), +Fylke(32) /Kommune(2))
//    )),
////        Move(Fylke(3), +Fylke(9)),
////        Move(Fylke(4), +Fylke(24)),
//////        Split(Fylke(51), listOf(
//////            Move(Kommune(1), +Fylke(11) / Kommune(1)),
//////        )),
//    MoveRange(Fylke(90), Fylke(99), +Fylke(1000)),
////        Adjust(
////            Fylke(51), listOf(
//////                Move(Kommune(3), +Fylke(222) / Kommune(1)),
////                Move(Kommune(11), +Fylke(11) / Kommune(2)),
//////                Move(Kommune(2), +Fylke(11) / Kommune(2)),
////                Adjust(
////                    Kommune(10), listOf(
////                        Move(Gard(1), +Fylke(2) / Kommune(1) / Gard(2))
////                    )
////                )
////            )
////        ),
////        Adjust(
////            Fylke(5), listOf(
////                Move(Kommune(5), +Fylke(11) / Kommune(3))
////            )
////        )
//)
////

