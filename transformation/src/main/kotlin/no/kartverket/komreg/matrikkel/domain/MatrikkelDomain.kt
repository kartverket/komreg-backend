package no.kartverket.komreg.matrikkel.domain

import arrow.core.Either
import arrow.core.right
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.transformation.ComponentDomain

private val fylkesnummerDomain =
    object : ComponentDomain.Integral<Fylkesnummer>(
        Fylkesnummer::class,
        minValue = Fylkesnummer(1),
        maxValue = Fylkesnummer(99)
    ) {
        override fun Long.toA(): Either<Nothing, Fylkesnummer> =
            Fylkesnummer(this).right()

        override fun Fylkesnummer.toLong(): Long = value
    }

val Fylkesnummer.Companion.domain:
        ComponentDomain<Fylkesnummer>
    get() = fylkesnummerDomain

private val kommunelopenummerDomain =
    object : ComponentDomain.Integral<Kommunenummer.Lopenummer>(
        Kommunenummer.Lopenummer::class,
        minValue = Kommunenummer.Lopenummer(1),
        maxValue = Kommunenummer.Lopenummer(99)
    ) {
        override fun Long.toA(): Either<Nothing, Kommunenummer.Lopenummer> =
            Kommunenummer.Lopenummer(this.toByte()).right()

        override fun Kommunenummer.Lopenummer.toLong(): Long = value.toLong()
    }

val Kommunenummer.Lopenummer.Companion.domain:
        ComponentDomain<Kommunenummer.Lopenummer>
    get() = kommunelopenummerDomain
