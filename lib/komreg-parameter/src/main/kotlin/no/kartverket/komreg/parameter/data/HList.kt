package no.kartverket.komreg.parameter.data

import arrow.core.nel
import no.kartverket.komreg.parameter.data.Tuple.Ap
import java.util.Spliterator

data class HList<Init : Tuple, Last>(
    override val init: Init, override val last: Last
) : Tuple.Ap<Init, Last> {
    override fun toString(): String {
        return when(init) {
            Tuple.Empty -> "("
            is Tuple.Ap<*, *> -> "$init, "
        } + last.toString() + ")"
    }

}

fun <Init : Tuple, Last> Init.append(last: Last): Ap<Init, Last> = HList(this, last)
fun <Init : Tuple, Last> Ap<out Init, out Last>.narrow(): Ap<Init, Last> = HList(init, last)
operator fun <Init : Tuple, Last> Init.times(last: Last): Ap<Init, Last> = HList(this, last)