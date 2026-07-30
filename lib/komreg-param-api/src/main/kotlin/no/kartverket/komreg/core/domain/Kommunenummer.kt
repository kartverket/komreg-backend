package no.kartverket.komreg.core.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.kartverket.komreg.parameter.data.CombinableType
import no.kartverket.komreg.parameter.data.FinalType
import no.kartverket.komreg.parameter.data.FinalType.Companion.invoke
import no.kartverket.komreg.parameter.data.KeyCombinableType

@Serializable
@SerialName("Kommunenummer")
data class Kommunenummer(val fylkesnummer: Fylkesnummer, val lopenummer: Lopenummer) : Comparable<Kommunenummer> {
    override fun compareTo(other: Kommunenummer): Int {
        return compareValuesBy(
            this,
            other,
            Kommunenummer::fylkesnummer,
            Kommunenummer::lopenummer,
        )
    }

    @Serializable
    @SerialName("Kommunelopenummer")
    data class Lopenummer(val value: Byte) : Comparable<Lopenummer> {
        override fun compareTo(other: Lopenummer): Int = value.compareTo(other.value)
        override fun toString(): String {
            return "Kommune($value)"
        }

        companion object
        object Type : KeyCombinableType<Lopenummer, Kommunedata> {
            override val valueType: CombinableType<Kommunedata> = object : CombinableType<Kommunedata> {
                override fun combine(
                    a: Kommunedata,
                    b: Kommunedata
                ): Either<String, Kommunedata> {
                    return if (a == b) a.right() else "Uncombinable: $a and $b".left()
                }

                override val finalType: FinalType<Kommunedata> = FinalType<Kommunedata>()

            }
            override val finalType: FinalType<Lopenummer> = FinalType<Lopenummer>()
        }
    }

    companion object {
        operator fun invoke(value: Long): Kommunenummer {
            val (fylkesnummer, lopenummer) = value.splitToFylkesnummerOgLopenummer()
            return Kommunenummer(
                Fylkesnummer(fylkesnummer.toLong()),
                Lopenummer(lopenummer.toByte()),
            )
        }
    }
}

private fun Long.splitToFylkesnummerOgLopenummer() = this.toString()
    .padStart(4, '0')
    .takeLast(4)
    .chunked(2)

fun Kommunenummer.verdi(): String {
    val fylkesnummer = this.fylkesnummer.verdi()
    val kommunelopenummer = this.lopenummer.verdi()
    return "$fylkesnummer$kommunelopenummer"
}

fun Kommunenummer.Lopenummer.verdi(): String {
    return this.value.toString().padStart(2, '0')
}
