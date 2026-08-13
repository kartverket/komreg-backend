package no.kartverket.komreg.core.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.kartverket.komreg.parameter.data.CombinableType
import no.kartverket.komreg.parameter.data.FinalType
import no.kartverket.komreg.parameter.data.KeyCombinableType

@Serializable
@SerialName("Fylkesnummer")
data class Fylkesnummer(val value: Long) : Comparable<Fylkesnummer> {
    override fun compareTo(other: Fylkesnummer): Int = value.compareTo(other.value)
    override fun toString(): String {
        return "Fylke($value)"
    }

    companion object

    object Type : KeyCombinableType<Fylkesnummer, Fylkesdata> {
        override val valueType: CombinableType<Fylkesdata> = object : CombinableType<Fylkesdata> {
            override fun combine(
                a: Fylkesdata,
                b: Fylkesdata
            ): Either<String, Fylkesdata> {
                return if (a == b) a.right() else "Uncombinable: $a and $b".left()
            }

            override val finalType: FinalType<Fylkesdata> = FinalType<Fylkesdata>()

        }
        override val finalType: FinalType<Fylkesnummer> = FinalType<Fylkesnummer>()
    }

}

fun Fylkesnummer.verdi() = value.toString().padStart(2, '0')
