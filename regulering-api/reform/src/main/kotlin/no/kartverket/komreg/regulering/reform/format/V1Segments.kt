package no.kartverket.komreg.regulering.reform.format

import arrow.core.raise.Raise
import arrow.core.right
import kotlinx.serialization.json.JsonPrimitive
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Kretsnummer
import no.kartverket.komreg.core.domain.Kretstype
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.core.domain.TeigId
import no.kartverket.komreg.regulering.reform.domain.type.AddressCode
import no.kartverket.komreg.regulering.reform.domain.type.AddressNumber
import no.kartverket.komreg.regulering.reform.domain.type.CountyNumber
import no.kartverket.komreg.regulering.reform.domain.type.DistrictNumber
import no.kartverket.komreg.regulering.reform.domain.type.DistrictType
import no.kartverket.komreg.regulering.reform.domain.type.LandNumber
import no.kartverket.komreg.regulering.reform.domain.type.MunicipalitySerial
import no.kartverket.komreg.regulering.reform.domain.type.NumberingScheme
import no.kartverket.komreg.regulering.reform.domain.type.ParcelId
import no.kartverket.komreg.regulering.reform.domain.type.TitleNumber
import kotlin.reflect.typeOf

/** Navnene på kindene i v1, slik de skrives i JSON. */
object V1Kinds {
    const val COUNTY = "county"
    const val MUNICIPALITY = "municipality"
    const val ADDRESS_CODE = "addressCode"
    const val ADDRESS_NUMBER = "addressNumber"
    const val DISTRICT_TYPE = "districtType"
    const val DISTRICT_NUMBER = "districtNumber"
    const val LAND_NUMBER = "landNumber"
    const val TITLE_NUMBER = "titleNumber"
    const val PARCEL = "parcel"
}

/**
 * Registeret med kindene i v1. Identtypene er de samme som `ReformMapping.kt` bruker. [scheme] avgjør hvilke
 * fylkesnummer som er gyldige.
 */
suspend fun SegmentRegistry.Companion.v1(scheme: NumberingScheme): SegmentRegistry {
    val county = SegmentKind(
        name = V1Kinds.COUNTY,
        type = CountyNumber::class,
        parent = null,
        componentTypes = listOf(typeOf<Fylkesnummer>()),
        decoder = { with(scheme) { CountyNumber(string(it)) } },
        expander = { single(Fylkesnummer(it.value.toLong())) },
        payload = PayloadKind(FylkePayload.serializer()) { payload, _ -> payload.toFylkesdata().right() },
    )
    val municipality = SegmentKind(
        name = V1Kinds.MUNICIPALITY,
        type = MunicipalitySerial::class,
        parent = county,
        componentTypes = listOf(typeOf<Kommunenummer.Lopenummer>()),
        decoder = { MunicipalitySerial(string(it)) },
        expander = { single(Kommunenummer.Lopenummer(it.value.toByte())) },
        payload = PayloadKind(KommunePayload.serializer()) { payload, dato -> payload.toKommunedata(dato) },
    )
    val addressCode = SegmentKind(
        name = V1Kinds.ADDRESS_CODE,
        type = AddressCode::class,
        parent = municipality,
        componentTypes = listOf(typeOf<Adressekode>()),
        decoder = { AddressCode(int(it)) },
        expander = { single(Adressekode(it.value)) },
    )
    val addressNumber = SegmentKind(
        name = V1Kinds.ADDRESS_NUMBER,
        type = AddressNumber::class,
        parent = addressCode,
        componentTypes = listOf(typeOf<Adressenummernummer>()),
        decoder = { AddressNumber(short(it)) },
        expander = { single(Adressenummernummer(it.value)) },
    )
    val districtType = SegmentKind(
        name = V1Kinds.DISTRICT_TYPE,
        type = DistrictType::class,
        parent = municipality,
        componentTypes = listOf(typeOf<Kretstype>()),
        decoder = { DistrictType(string(it).also { s -> ensureNotBlank(s) }) },
        expander = { single(Kretstype(it.value)) },
    )
    val districtNumber = SegmentKind(
        name = V1Kinds.DISTRICT_NUMBER,
        type = DistrictNumber::class,
        parent = districtType,
        componentTypes = listOf(typeOf<Kretsnummer>()),
        decoder = { DistrictNumber(long(it)) },
        expander = { single(Kretsnummer(it.value)) },
    )
    val landNumber = SegmentKind(
        name = V1Kinds.LAND_NUMBER,
        type = LandNumber::class,
        parent = municipality,
        componentTypes = listOf(typeOf<Matrikkelnummer.Gardsnummer>()),
        decoder = { LandNumber(int(it)) },
        expander = { single(Matrikkelnummer.Gardsnummer(it.value)) },
    )
    val titleNumber = SegmentKind(
        name = V1Kinds.TITLE_NUMBER,
        type = TitleNumber::class,
        parent = landNumber,
        componentTypes = listOf(typeOf<Matrikkelnummer.Bruksnummer>()),
        decoder = { TitleNumber(int(it)) },
        expander = { single(Matrikkelnummer.Bruksnummer(it.value)) },
    )
    // En teig er fiktivt registrert på gnr 0, bnr 0 («mnr mangler») og bnr 1 («mnr vann mangler»), fnr 0 og
    // snr 0. Dagens DTO flytter alltid begge, så kinden gir to identer. Komponentene lages direkte, uten
    // LeaseNumber/UnitNumber, som ikke tillater 0.
    val parcel = SegmentKind(
        name = V1Kinds.PARCEL,
        type = ParcelId::class,
        parent = municipality,
        componentTypes = listOf(
            typeOf<Matrikkelnummer.Gardsnummer>(),
            typeOf<Matrikkelnummer.Bruksnummer>(),
            typeOf<Matrikkelnummer.Festenummer>(),
            typeOf<Matrikkelnummer.Seksjonsnummer>(),
            typeOf<TeigId>(),
        ),
        decoder = { ParcelId(long(it)) },
        expander = { parcel ->
            listOf<Short>(0, 1).map { bnr ->
                listOf(
                    Matrikkelnummer.Gardsnummer(0),
                    Matrikkelnummer.Bruksnummer(bnr),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                    TeigId(parcel.value),
                )
            }
        },
    )

    return SegmentRegistry.of(
        listOf(
            county,
            municipality,
            addressCode,
            addressNumber,
            districtType,
            districtNumber,
            landNumber,
            titleNumber,
            parcel,
        ),
    )
}

private fun single(component: Comparable<*>): List<List<Comparable<*>>> = listOf(listOf(component))

private fun Raise<String>.string(value: JsonPrimitive): String =
    if (value.isString) value.content else raise("forventet en streng")

private fun Raise<String>.ensureNotBlank(value: String) {
    if (value.isBlank()) raise("kan ikke være tom")
}

private fun Raise<String>.long(value: JsonPrimitive): Long =
    if (value.isString) raise("forventet et tall") else value.content.toLongOrNull() ?: raise("forventet et heltall")

private fun Raise<String>.int(value: JsonPrimitive): Int =
    long(value).let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else raise("utenfor gyldig område") }

private fun Raise<String>.short(value: JsonPrimitive): Short =
    long(value).let { if (it in Short.MIN_VALUE..Short.MAX_VALUE) it.toShort() else raise("utenfor gyldig område") }
