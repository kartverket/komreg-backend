package no.kartverket.komreg.core.domain.ident

import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.core.domain.FylkeIdent
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.KommuneIdent
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.*
import no.kartverket.komreg.integration.spi.*

val FylkeIdent: IdentType1<Fylkesnummer> = runBlocking { identTypeOf1() }
val KommuneIdent: IdentType2<Fylkesnummer, Kommunenummer.Lopenummer> = runBlocking { identTypeOf2() }
val GardsnummerIdent: IdentType3<Fylkesnummer, Kommunenummer.Lopenummer, Gardsnummer> = runBlocking { identTypeOf3() }
val BruksnummmerIdent: IdentType4<Fylkesnummer, Kommunenummer.Lopenummer, Gardsnummer, Bruksnummer> =
        runBlocking { identTypeOf4() }

operator fun FylkeIdent.div(kommuneIdent: Kommunenummer.Lopenummer): KommuneIdent {
    return appendWith(Kommune.KommuneIdent, kommuneIdent)
}

suspend inline operator fun <T : Ident, A : Comparable<A>, reified B : Comparable<B>> Ident.And<T, A>.div(that: B): Ident.And<Ident.And<T, A>, B> {
    return with(this@div.type.append<B>()) {
        this@div.appendWith(this, that)
    }
}

suspend inline operator fun <reified B : Comparable<B>> Ident.Empty.div(that: B): Ident1<B> {
    return with(this@div.type.append<B>()) {
        this@div.appendWith(this, that)
    }
}