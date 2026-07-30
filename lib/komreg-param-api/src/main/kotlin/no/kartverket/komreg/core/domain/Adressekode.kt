package no.kartverket.komreg.core.domain

import arrow.core.Either
import arrow.core.right
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.kartverket.komreg.parameter.data.CombinableType
import no.kartverket.komreg.parameter.data.EnumerableType
import no.kartverket.komreg.parameter.data.Enumerator
import no.kartverket.komreg.parameter.data.FinalType
import no.kartverket.komreg.parameter.data.FinalType.Companion.invoke
import no.kartverket.komreg.parameter.data.KeyCombinableType

@Serializable
@SerialName("Adressekode")
data class Adressekode(
    val value: Int
) : Comparable<Adressekode> {
    override fun compareTo(other: Adressekode): Int =
        value.compareTo(other.value)

    object Type : EnumerableType<Adressekode>, KeyCombinableType<Adressekode, Unit> {
        override val enumerator: Enumerator<Adressekode>
            get() = TODO("Not yet implemented")
        override val finalType: FinalType<Adressekode>
            get() = FinalType<Adressekode>()
        override val valueType: CombinableType<Unit> = object : CombinableType<Unit> {
            override fun combine(
                a: Unit,
                b: Unit
            ): Either<String, Unit> {
                return Unit.right()
            }

            override val finalType: FinalType<Unit> = FinalType()

        }
    }
}
