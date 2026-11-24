package no.kartverket.komreg.transformation.reform

import arrow.core.getOrElse
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Bygningsnummer
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Kretsnummer
import no.kartverket.komreg.core.domain.Kretstype
import no.kartverket.komreg.core.domain.Matrikkelnummer.Bruksnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Festenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Seksjonsnummer
import no.kartverket.komreg.core.domain.TeigId
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.IdentOrEmptyType
import no.kartverket.komreg.integration.spi.Payload
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.integration.spi.identTypeOf1
import no.kartverket.komreg.integration.spi.identTypeOf3
import no.kartverket.komreg.integration.spi.identTypeOf4
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.transformation.TestIdType
import no.kartverket.komreg.transformation.getAdresseparsellIdentType
import no.kartverket.komreg.transformation.getBygningIdentType
import no.kartverket.komreg.transformation.getKommuneIdentType
import no.kartverket.komreg.transformation.getKretsIdentType
import no.kartverket.komreg.transformation.getMatrikkelenhetIdentType
import no.kartverket.komreg.transformation.getTeigIdentType
import no.kartverket.komreg.transformation.getVegadresseIdentType

internal fun fnr(n: Long) = Fylkesnummer(n)
internal fun klnr(n: Int) = Kommunenummer.Lopenummer(n.toByte())

internal suspend fun fylke(f: Long) = identTypeOf1<Fylkesnummer>()(fnr(f))
internal suspend fun kommune(f: Long, l: Int) = getKommuneIdentType()(fnr(f), klnr(l))
internal suspend fun gnr(f: Long, l: Int, g: Int) =
    identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Gardsnummer>()(fnr(f), klnr(l), Gardsnummer(g))
internal suspend fun grunneiendom(f: Long, l: Int, g: Int, b: Short) =
    identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Gardsnummer, Bruksnummer>()(
        fnr(f), klnr(l), Gardsnummer(g), Bruksnummer(b),
    )
internal suspend fun matrikkelenhet(f: Long, l: Int, g: Int, b: Short) =
    getMatrikkelenhetIdentType()(fnr(f), klnr(l), Gardsnummer(g), Bruksnummer(b), Festenummer(0), Seksjonsnummer(0))
internal suspend fun bygning(f: Long, l: Int, nr: Long) = getBygningIdentType()(fnr(f), klnr(l), Bygningsnummer(nr))
internal suspend fun veg(f: Long, l: Int, a: Int) = getAdresseparsellIdentType()(fnr(f), klnr(l), Adressekode(a))
internal suspend fun vegadresse(f: Long, l: Int, a: Int, n: Short) =
    getVegadresseIdentType()(fnr(f), klnr(l), Adressekode(a), Adressenummernummer(n))
internal suspend fun krets(f: Long, l: Int, type: String, nr: Long) =
    getKretsIdentType()(fnr(f), klnr(l), Kretstype(type), Kretsnummer(nr))
internal suspend fun teig(f: Long, l: Int, g: Int, b: Short, teigId: Long) =
    getTeigIdentType()(fnr(f), klnr(l), Gardsnummer(g), Bruksnummer(b), Festenummer(0), Seksjonsnummer(0), TeigId(teigId))

/** `IdTypeProvider` for testene: fylker og kommuner har egne `IdType`-er, resten har ingen. */
internal suspend fun testIdTypeFor(type: IdentOrEmptyType<*>): IdType<*, *>? = when {
    type === identTypeOf1<Fylkesnummer>() -> TestIdType.Fylke
    type === getKommuneIdentType() -> TestIdType.Kommune
    else -> null
}

/**
 * Deterministisk `idFor`: samme `(IdType, hint)` gir alltid samme ID, uavhengig av kallrekkefølge. Deles mellom
 * dagens og ny kjøring i sammenligningen, slik at ID-ene for nye entiteter kan sammenlignes.
 */
internal class MemoIdFor(private var next: Long = 1000) {
    private val ids = HashMap<Pair<IdType<*, *>, Any?>, Id>()

    @Suppress("RedundantSuspendModifier")
    suspend operator fun invoke(type: IdType<*, *>, hint: Any?): Id =
        ids.getOrPut(type to hint) { Id(type as TestIdType, next++) }
}

/** Det sinkene ser av en transformasjon. */
internal data class Row(val id: Id, val ident: Ident?, val assoc: Set<Ident>?, val result: Payload?)

internal fun Transformation.row() = Row(id, transformedIdent, transformedAssociatedIdents, resultObject)

internal fun claimsOf(ops: List<ReformOp>): ReformClaims =
    ReformClaims.validate(ops).getOrElse { errs -> error(errs.joinToString { it.message }) }

internal fun transformerOf(ops: List<ReformOp>) = ReformTransformer(claimsOf(ops), ::testIdTypeFor)

internal fun fooId(n: Long) = Id(TestIdType.Foo, n)
internal fun kommuneId(n: Long) = Id(TestIdType.Kommune, n)
internal fun fylkeId(n: Long) = Id(TestIdType.Fylke, n)
