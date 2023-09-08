package no.kartverket.komreg.matrikkel.domain

import arrow.core.Either
import arrow.core.right
import no.kartverket.komreg.core.domain.*
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



private val gardsnummerDomain =
    object : ComponentDomain.Integral<Matrikkelnummer.Gardsnummer>(
        Matrikkelnummer.Gardsnummer::class,
        minValue = Matrikkelnummer.Gardsnummer(0),
        maxValue = Matrikkelnummer.Gardsnummer(99999)
    ) {
        override fun Long.toA(): Either<Nothing, Matrikkelnummer.Gardsnummer> =
            Matrikkelnummer.Gardsnummer(this.toInt()).right()

        override fun Matrikkelnummer.Gardsnummer.toLong(): Long = value.toLong()
    }

val Matrikkelnummer.Gardsnummer.Companion.domain: ComponentDomain<Matrikkelnummer.Gardsnummer>
    get() = gardsnummerDomain

private val bruksnummerDomain =
    object : ComponentDomain.Integral<Matrikkelnummer.Bruksnummer>(
        Matrikkelnummer.Bruksnummer::class,
        minValue = Matrikkelnummer.Bruksnummer(0),
        maxValue = Matrikkelnummer.Bruksnummer(9999)
    ) {
        override fun Long.toA(): Either<Nothing, Matrikkelnummer.Bruksnummer> =
            Matrikkelnummer.Bruksnummer(this.toShort()).right()

        override fun Matrikkelnummer.Bruksnummer.toLong(): Long = value.toLong()
    }

val Matrikkelnummer.Bruksnummer.Companion.domain: ComponentDomain<Matrikkelnummer.Bruksnummer>
    get() = bruksnummerDomain

private val festenummerDomain =
    object : ComponentDomain.Integral<Matrikkelnummer.Festenummer>(
        Matrikkelnummer.Festenummer::class,
        minValue = Matrikkelnummer.Festenummer(0),
        maxValue = Matrikkelnummer.Festenummer(9999)
    ) {
        override fun Long.toA(): Either<Nothing, Matrikkelnummer.Festenummer> =
            Matrikkelnummer.Festenummer(this.toShort()).right()

        override fun Matrikkelnummer.Festenummer.toLong(): Long = value.toLong()
    }

val Matrikkelnummer.Festenummer.Companion.domain: ComponentDomain<Matrikkelnummer.Festenummer>
    get() = festenummerDomain

private val seksjonsnummerDomain =
    object : ComponentDomain.Integral<Matrikkelnummer.Seksjonsnummer>(
        Matrikkelnummer.Seksjonsnummer::class,
        minValue = Matrikkelnummer.Seksjonsnummer(0),
        maxValue = Matrikkelnummer.Seksjonsnummer(9999)
    ) {
        override fun Long.toA(): Either<Nothing, Matrikkelnummer.Seksjonsnummer> =
            Matrikkelnummer.Seksjonsnummer(this.toShort()).right()

        override fun Matrikkelnummer.Seksjonsnummer.toLong(): Long = value.toLong()
    }

val Matrikkelnummer.Seksjonsnummer.Companion.domain: ComponentDomain<Matrikkelnummer.Seksjonsnummer>
    get() = seksjonsnummerDomain



private val adressekodeDomain =
    object : ComponentDomain.Integral<Adressekode>(
        Adressekode::class,
        minValue = Adressekode(1),
        maxValue = Adressekode(99999)
    ) {
        override fun Long.toA(): Either<Nothing, Adressekode> = Adressekode(this.toInt()).right()

        override fun Adressekode.toLong(): Long = value.toLong()
    }

val Adressekode.Companion.domain: ComponentDomain<Adressekode>
    get() = adressekodeDomain



private val adressenummernummerDomain =
    object : ComponentDomain.Integral<Adressenummernummer>(
        Adressenummernummer::class,
        minValue = Adressenummernummer(1),
        maxValue = Adressenummernummer(9998)
    ) {
        override fun Long.toA(): Either<Nothing, Adressenummernummer> = Adressenummernummer(this.toShort()).right()

        override fun Adressenummernummer.toLong(): Long = value.toLong()
    }

val Adressenummernummer.Companion.domain: ComponentDomain<Adressenummernummer>
    get() = adressenummernummerDomain

private val adressenummerbokstavDomain =
    object : ComponentDomain.Uncountable<Adressenummerbokstav>(Adressenummerbokstav::class) {}

val Adressenummerbokstav.Companion.domain: ComponentDomain<Adressenummerbokstav>
    get() = adressenummerbokstavDomain



private val kretstypeDomain = object : ComponentDomain.Uncountable<Kretstype>(Kretstype::class) {}

val Kretstype.Companion.domain: ComponentDomain<Kretstype>
    get() = kretstypeDomain

private val kretsnummerDomain =
    object : ComponentDomain.Integral<Kretsnummer>(
        Kretsnummer::class,
        minValue = Kretsnummer(1),
        maxValue = Kretsnummer(99999999)
    ) {
        override fun Long.toA(): Either<Nothing, Kretsnummer> = Kretsnummer(this).right()

        override fun Kretsnummer.toLong(): Long = value
    }

val Kretsnummer.Companion.domain: ComponentDomain<Kretsnummer>
    get() = kretsnummerDomain



private val bygningsnummerDomain =
    object : ComponentDomain.Integral<Bygningsnummer>(
        Bygningsnummer::class,
        minValue = Bygningsnummer(1),
        maxValue = null
    ) {
        override fun Long.toA(): Either<Nothing, Bygningsnummer> = Bygningsnummer(this).right()

        override fun Bygningsnummer.toLong(): Long = value
    }

val Bygningsnummer.Companion.domain: ComponentDomain<Bygningsnummer>
    get() = bygningsnummerDomain
