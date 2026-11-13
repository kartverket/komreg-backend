package no.kartverket.komreg.regulering.api.server.service.matrikkelapi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import no.kartverket.komreg.regulering.api.server.domain.MunicipalityId
import no.kartverket.komreg.regulering.reform.domain.type.CadastralNumber
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleIdList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleObject
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelContext
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.adresse.Adresse
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.adresse.Veg
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.adresse.VegId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.adresse.Vegadresse
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.adresse.VegadresseId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.bygning.ByggIdList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Arc
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.CurveDirection
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Polyline
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Festegrunn
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.MatrikkelenhetId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.MatrikkelenhetIdList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Matrikkelnummer
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Seksjon
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Teig
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.TeigId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.TeigIdList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Teiggrense
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.TeiggrenseId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Teiggrensepunkt
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.TeiggrensepunktId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.adresse.AdresseService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.bygning.BygningService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.matrikkelenhet.MatrikkelenhetService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.store.StoreService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.teig.TeigService
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.geojson.dsl.PolygonBuilder
import org.maplibre.spatialk.geojson.dsl.addFeature
import org.maplibre.spatialk.geojson.dsl.addPoint
import org.maplibre.spatialk.geojson.dsl.addRing
import org.maplibre.spatialk.geojson.dsl.buildFeatureCollection
import org.maplibre.spatialk.geojson.dsl.buildPolygon
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Matrikkelenhet as WSMatrikkelenhet

// En krets kan gi tusenvis av teiggrenser og grensepunkter, og klienten har 10 s timeout per kall.
private const val GET_OBJECTS_CHUNK_SIZE = 1000

fun StoreService.getObjectsChunked(ids: List<MatrikkelBubbleId>, ctx: MatrikkelContext): List<MatrikkelBubbleObject> =
    ids.chunked(GET_OBJECTS_CHUNK_SIZE).flatMap { getObjects(MatrikkelBubbleIdList(it), ctx).item }

val EPSG_25833_CRS: JsonObject = buildJsonObject {
    put("type", "name")
    put("properties", buildJsonObject {
        put("name", "urn:ogc:def:crs:EPSG::25833")
    })
}

/**
 * Bygger teiger og adresser som GeoJSON ut fra et sett startteiger. Felles for klikk i kartet
 * (én startteig) og henting av en hel grunnkrets (mange).
 */
class CadastralUnitFeatures(
    private val matrikkelenhetService: MatrikkelenhetService,
    private val teigService: TeigService,
    private val storeService: StoreService,
    private val bygningService: BygningService,
    private val adresseService: AdresseService,
    private val ctx: MatrikkelContext,
    private val mapper: MatrikkelApiMapper,
    private val appJson: Json,
) {
    /**
     * Startteigene utvides med de andre teigene til de samme matrikkelenhetene, samt festegrunner
     * og seksjoner, så resultatet kan inneholde flere teiger enn [startParcelIds].
     * [parcelProperties] lar kalleren merke hver teig, for eksempel om den var en startteig.
     */
    fun collect(
        startParcelIds: Collection<TeigId>,
        parcelProperties: JsonObjectBuilder.(TeigId) -> Unit,
    ): FeatureCollection<Geometry?, JsonObject?> {
        val parcelIdQueue = ArrayDeque(startParcelIds)
        val seenParcelIds = hashSetOf<TeigId>()
        val cadastralUnitIdQueue = ArrayDeque<MatrikkelenhetId>()
        val seenCadastralUnitIds = hashMapOf<MatrikkelenhetId, WSMatrikkelenhet>()
        val parcelCadastralUnits = hashMapOf<TeigId, MutableSet<Matrikkelnummer>>()
        val includedParcelIds = startParcelIds.toHashSet()

        while (parcelIdQueue.isNotEmpty() || cadastralUnitIdQueue.isNotEmpty()) {
            if (parcelIdQueue.isNotEmpty()) {
                matrikkelenhetService.findMatrikkelenheterForTeiger(TeigIdList(parcelIdQueue.toList()), ctx)
                    .entry
                    .asSequence()
                    .filter { seenParcelIds.add(it.key) }
                    .flatMap { it.value.item.asSequence() }
                    .filterNot { seenCadastralUnitIds.contains(it) }
                    .forEach {
                        cadastralUnitIdQueue.addLast(it)
                    }
                parcelIdQueue.clear()
            }

            if (cadastralUnitIdQueue.isNotEmpty()) {
                val cadastralUnitsFetched = storeService
                    .getObjectsChunked(cadastralUnitIdQueue.toList(), ctx)
                    .map { it as WSMatrikkelenhet }
                    .associateBy { it.id as MatrikkelenhetId }
                seenCadastralUnitIds.putAll(cadastralUnitsFetched)

                val leasedUnitIds = matrikkelenhetService
                    .findFestegrunnerForMatrikkelenheter(MatrikkelenhetIdList(cadastralUnitIdQueue.toList()), false, ctx)
                    .entry
                    .asSequence()
                    .flatMap { it.value.item.asSequence() }
                    .filterNot(seenCadastralUnitIds::contains)
                val sectionedUnitIds = matrikkelenhetService
                    .findSeksjonerForMatrikkelenheter(MatrikkelenhetIdList(cadastralUnitIdQueue.toList()), false, ctx)
                    .entry
                    .asSequence()
                    .flatMap { it.value.item.asSequence() }
                    .filterNot(seenCadastralUnitIds::contains)

                cadastralUnitIdQueue.clear()

                val leasedFromIds = cadastralUnitsFetched
                    .values
                    .asSequence()
                    .mapNotNull { it as? Festegrunn }
                    .flatMap { it.festetMatrikkelenhetIds.item.asSequence() }

                val sectionedFromIds = cadastralUnitsFetched
                    .values
                    .asSequence()
                    .mapNotNull { it as? Seksjon }
                    .flatMap { it.seksjonertMatrikkelenhetIds.item.asSequence() }

                (leasedUnitIds + sectionedUnitIds + leasedFromIds + sectionedFromIds)
                    .filterNot { seenCadastralUnitIds.contains(it) }
                    .distinct()
                    .forEach { cadastralUnitIdQueue.addLast(it) }

                for ((_, unit) in cadastralUnitsFetched) {
                    for (tfm in unit.teigerForMatrikkelenhet.item) {
                        parcelCadastralUnits
                            .getOrPut(tfm.teigId) { hashSetOf() }
                            .add(unit.matrikkelnummer)

                        if (unit.matrikkelnummer.gardsnummer != 0 &&
                            includedParcelIds.add(tfm.teigId)
                        ) {
                            parcelIdQueue.addLast(tfm.teigId)
                        }
                    }
                }
            }
        }

        val cadestralUnitsIds = seenCadastralUnitIds.values.asSequence()
            .filter { it.matrikkelnummer.gardsnummer != 0 }
            .map { it.id as MatrikkelenhetId }
            .toList()

        val addressCadastralUnits = hashMapOf<VegadresseId, MutableSet<Matrikkelnummer>>()
        val (directAddressIds, indirectAddressIds) = if (cadestralUnitsIds.isNotEmpty()) {
            val directAddresses = adresseService
                .findAdresserForMatrikkelenheter(MatrikkelenhetIdList(cadestralUnitsIds), ctx)
                .entry
            for (entry in directAddresses) {
                val cadastralNumber = seenCadastralUnitIds[entry.key]!!.matrikkelnummer
                for (addressId in entry.value.item.filterIsInstance<VegadresseId>()) {
                    addressCadastralUnits.getOrPut(addressId) { hashSetOf() }.add(cadastralNumber)
                }
            }
            val direct: Sequence<VegadresseId> =
                directAddresses.asSequence().flatMap { it.value.item.asSequence().filterIsInstance<VegadresseId>() }

            val buildingIdsByUnit = bygningService
                .findByggForMatrikkelenheter(MatrikkelenhetIdList(cadestralUnitsIds), ctx)
                .entry
            val buildingIds = buildingIdsByUnit.flatMap { it.value.item }.distinct()
            val buildingAddresses = if (buildingIds.isNotEmpty()) {
                adresseService.findAdresserForByggList(ByggIdList(buildingIds), ctx).entry
            } else {
                emptyList()
            }
            val addressIdsByBuilding = buildingAddresses.associate {
                it.key.value to it.value.item.filterIsInstance<VegadresseId>()
            }
            for (entry in buildingIdsByUnit) {
                val cadastralNumber = seenCadastralUnitIds[entry.key]!!.matrikkelnummer
                for (byggId in entry.value.item) {
                    for (addressId in addressIdsByBuilding[byggId.value] ?: emptyList()) {
                        addressCadastralUnits.getOrPut(addressId) { hashSetOf() }.add(cadastralNumber)
                    }
                }
            }
            val indirect: Sequence<VegadresseId> =
                addressIdsByBuilding.values.asSequence().flatMap { it.asSequence() }

            direct to indirect
        } else {
            emptySequence<VegadresseId>() to emptySequence()
        }

        val parcelIdsForAddresses = seenCadastralUnitIds.values.asSequence()
            .filter { it.matrikkelnummer.gardsnummer == 0}
            .flatMap { it.teigerForMatrikkelenhet.item.asSequence() }
            .map { it.teigId }
            .filter { includedParcelIds.contains(it) }
            .toList()
        val parcelAddressIds = if (parcelIdsForAddresses.isNotEmpty()) {
            teigService.findAdresserForTeiger(TeigIdList(parcelIdsForAddresses), ctx)
                .item.asSequence().filterIsInstance<VegadresseId>()
        } else {
            emptySequence()
        }

        val addressIds = (directAddressIds + indirectAddressIds + parcelAddressIds).distinct().toList()
        val adresses = if (addressIds.isNotEmpty()) {
            storeService.getObjectsChunked(addressIds, ctx)
                .asSequence()
                .map { it as Adresse }
                .mapNotNull { it as? Vegadresse }
                .associateBy { it.id as VegadresseId }
        } else {
            emptyMap()
        }

        val roadIds = adresses.values.map { it.vegId }.distinct()
        val roads = if (roadIds.isNotEmpty()) {
            storeService.getObjectsChunked(roadIds, ctx)
                .asSequence().map { it as Veg }.associateBy { it.id as VegId }
        } else {
            emptyMap()
        }

        val teiger = storeService
            .getObjectsChunked(includedParcelIds.toList(), ctx)
            .asSequence()
            .map { it as Teig }.associateByTo(hashMapOf()) { it.id as TeigId }

        val exteriorCds = teiger.values.flatMap { it.flate.exterior.curveDirections.item }
        val interiorCds = teiger.values.flatMap { it.flate.interior.item }.map { it.curveDirections.item }
        val grenselinjeIds = (exteriorCds + interiorCds.flatten()).asSequence().map { it.grenselinjeId }.distinct().toList()

        val grenselinjer = if (grenselinjeIds.isNotEmpty()) {
            storeService.getObjectsChunked(grenselinjeIds, ctx)
                .map { it as Teiggrense }
                .associateBy { it.id as TeiggrenseId }
        } else {
            emptyMap()
        }

        val grensepunktIds = grenselinjer
            .values
            .asSequence()
            .flatMap {
                sequenceOf(it.kurve.startpunktId, it.kurve.endpunktId)
            }
            .distinct()
            .toList()

        val grensepunkter = if (grensepunktIds.isNotEmpty()) {
            storeService.getObjectsChunked(grensepunktIds, ctx)
                .asSequence()
                .map { it as Teiggrensepunkt }
                .associateBy { it.id as TeiggrensepunktId }
        } else {
            emptyMap()
        }

        return with(mapper) {
            buildFeatureCollection {
                for ((id, vegadresse) in adresses) {
                    val point = vegadresse.representasjonspunkt?.position?.run { Point(x, y) } ?: continue

                    addFeature {
                        geometry = point
                        foreignMembers = buildJsonObject {
                            put("featureType", "Address")
                        }
                        setId(id.value)
                        val road = roads[vegadresse.vegId]!!
                        properties = buildJsonObject {
                            put("municipalityNumber", municipalityNumbers[MunicipalityId(road.kommuneId.value)].value)
                            put("addressCode", road.adressekode)
                            put("addressName", road.adressenavn)
                            put("addressNumber", vegadresse.nummer)
                            put("addressNumberExtension", vegadresse.bokstav)
                            putJsonArray("cadastralNumbers") {
                                for (matrikkelnummer in addressCadastralUnits[id] ?: emptySet()) {
                                    add(appJson.encodeToJsonElement(CadastralNumber.serializer(), matrikkelnummer.toDomain()))
                                }
                            }
                        }
                    }
                }
                for (teig in teiger.values) {
                    val teigPoly = buildPolygon {
                        addRing(teig.flate.exterior.curveDirections.item, grenselinjer, grensepunkter)
                        for (interior in teig.flate.interior.item) {
                            addRing(interior.curveDirections.item, grenselinjer, grensepunkter)
                        }
                    }

                    addFeature {
                        geometry = teigPoly
                        setId(teig.id.value)
                        properties = buildJsonObject {
                            putJsonArray("cadastralNumbers") {
                                for (matrikkelnummer in parcelCadastralUnits[teig.id] ?: emptySet()) {
                                    val cadastralNumber = matrikkelnummer.toDomain()
                                    add(appJson.encodeToJsonElement(CadastralNumber.serializer(), cadastralNumber))
                                }
                            }
                            parcelProperties(teig.id as TeigId)
                        }
                        foreignMembers = buildJsonObject {
                            put("featureType", "Parcel")
                        }
                    }
                }
                foreignMembers = buildJsonObject {
                    put("crs", EPSG_25833_CRS)
                }
            }
        }
    }
}

private fun PolygonBuilder.addRing(
    curveDirections: List<CurveDirection>,
    grenselinjer: Map<TeiggrenseId, Teiggrense>,
    grensepunkter: Map<TeiggrensepunktId, Teiggrensepunkt>
) {
    addRing {
        for (cd in curveDirections) {
            val grenseselinje = grenselinjer[cd.grenselinjeId]!!
            val startpunkt = grensepunkter[grenseselinje.kurve.startpunktId]!!
            val endpunkt = grensepunkter[grenseselinje.kurve.endpunktId]!!
            val midpoints = when (val kurve = grenseselinje.kurve) {
                // TODO: Hvordan håndtere kurver?
                is Arc -> listOf(
                    Position(
                        kurve.buepunkt.x,
                        kurve.buepunkt.y
                    )
                )

                is Polyline -> kurve.kurvepunkter.item.map {
                    Position(
                        it.x,
                        it.y
                    )
                }

                else -> throw AssertionError("Unexpected curve type: ${grenseselinje.kurve::class.simpleName}")
            }

            if (cd.isSigned) {
                addPoint(
                    Position(
                        startpunkt.posisjon.x,
                        startpunkt.posisjon.y
                    )
                )
                midpoints.forEach { add(it) }
                addPoint(
                    Position(
                        endpunkt.posisjon.x,
                        endpunkt.posisjon.y
                    )
                )
            } else {
                addPoint(
                    Position(
                        endpunkt.posisjon.x,
                        endpunkt.posisjon.y
                    )
                )
                midpoints.reversed().forEach { add(it) }
                addPoint(
                    Position(
                        startpunkt.posisjon.x,
                        startpunkt.posisjon.y
                    )
                )
            }
        }
    }
}
