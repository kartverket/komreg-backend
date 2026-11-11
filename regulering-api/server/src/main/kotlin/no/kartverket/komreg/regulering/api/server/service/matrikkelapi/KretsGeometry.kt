package no.kartverket.komreg.regulering.api.server.service.matrikkelapi

import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Multipolygon
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Ring
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Teig
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Location
import org.maplibre.spatialk.geojson.MultiPolygon
import org.maplibre.spatialk.geojson.Position

private val geometryFactory = GeometryFactory()

/**
 * Teigene som hører til kretsen: de som har representasjonspunktet sitt i kretsflaten.
 *
 * Krets- og teiggrensene er målt uavhengig av hverandre og avviker med meter akkurat langs
 * kretsgrensen. Representasjonspunktet ligger inne i teigen, så avviket flytter det ikke, og
 * hver teig havner i nøyaktig én krets. Teiger uten representasjonspunkt kan ikke plasseres og
 * tas ikke med.
 */
fun teigerInKrets(teiger: Collection<Teig>, kretsflate: Multipolygon): List<Teig> {
    val locator = IndexedPointInAreaLocator(kretsflate.toJts())
    return teiger.filter { teig ->
        val position = teig.representasjonspunkt?.position ?: return@filter false
        locator.locate(Coordinate(position.x, position.y)) != Location.EXTERIOR
    }
}

fun Multipolygon.toGeoJson(): MultiPolygon = MultiPolygon(
    polygoner.item.map { polygon ->
        (listOf(polygon.ytreAvgrensning) + polygon.indreAvgrensning?.item.orEmpty())
            .map { ring -> ring.closedCoordinates().map { Position(it.x, it.y) } }
    }
)

private fun Multipolygon.toJts(): org.locationtech.jts.geom.MultiPolygon =
    geometryFactory.createMultiPolygon(
        polygoner.item.map { polygon ->
            geometryFactory.createPolygon(
                polygon.ytreAvgrensning.toJts(),
                polygon.indreAvgrensning?.item.orEmpty().map { it.toJts() }.toTypedArray()
            )
        }.toTypedArray()
    )

private fun Ring.toJts() = geometryFactory.createLinearRing(closedCoordinates().toTypedArray())

private fun Ring.closedCoordinates(): List<Coordinate> {
    val coordinates = positions.item.map { Coordinate(it.x, it.y) }
    return if (coordinates.first().equals2D(coordinates.last())) coordinates else coordinates + coordinates.first()
}
