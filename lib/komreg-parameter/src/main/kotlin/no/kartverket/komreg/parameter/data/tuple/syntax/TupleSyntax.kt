package no.kartverket.komreg.parameter.data.tuple.syntax

import no.kartverket.komreg.parameter.data.HList
import no.kartverket.komreg.parameter.data.Tuple

operator fun <Init : Tuple, Last : Comparable<Last>> Init.div(last: Last): Tuple.Ap<Init, Last> = HList(this, last)
operator fun <Last : Comparable<Last>> Last.unaryPlus(): Tuple.Ap<Tuple.Empty, Last> = HList(Tuple.Empty, this)