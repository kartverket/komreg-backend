package no.kartverket.komreg.regulering.api.server.service.matrikkelapi

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.kotest.core.spec.style.FunSpec
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Multipolygon
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Point
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.PointList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Polygon
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.PolygonList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Position
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Representasjonspunkt
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Ring
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.RingList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Teig
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.TeigId
import org.maplibre.spatialk.geojson.Position as GeoJsonPosition

class KretsGeometryTest : FunSpec({
    // Ringer uten gjentatt startpunkt, for å dekke at de lukkes
    fun square(minX: Double, minY: Double, size: Double) = Ring(PointList().apply {
        item += Point(minX, minY, null)
        item += Point(minX + size, minY, null)
        item += Point(minX + size, minY + size, null)
        item += Point(minX, minY + size, null)
    })

    fun polygon(exterior: Ring, vararg holes: Ring) =
        Polygon(null, RingList().apply { item += holes }, exterior)

    fun teig(id: Long, x: Double, y: Double) = Teig().apply {
        this.id = TeigId(id)
        representasjonspunkt = Representasjonspunkt().apply {
            position = Position().apply { this.x = x; this.y = y }
        }
    }

    // Hoveddel 0..10 med et hull 4..6, og en øy 20..22
    val kretsflate = Multipolygon(null, PolygonList().apply {
        item += polygon(square(0.0, 0.0, 10.0), square(4.0, 4.0, 2.0))
        item += polygon(square(20.0, 20.0, 2.0))
    })

    test("teiger med representasjonspunktet i kretsflaten hører til kretsen") {
        val teiger = listOf(
            teig(1, 1.0, 1.0),   // i hoveddelen
            teig(2, 5.0, 5.0),   // i hullet
            teig(3, 21.0, 21.0), // på øya
            teig(4, 15.0, 15.0), // mellom delene
            teig(5, -1.0, 5.0),  // utenfor
            Teig().apply { id = TeigId(6) }, // uten representasjonspunkt
        )

        assertThat(teigerInKrets(teiger, kretsflate).map { it.id.value }).containsExactly(1L, 3L)
    }

    test("kretsflaten blir et GeoJSON MultiPolygon med lukkede ringer og hull") {
        val coordinates = kretsflate.toGeoJson().coordinates

        assertThat(coordinates.map { polygon -> polygon.map { it.size } }).isEqualTo(listOf(listOf(5, 5), listOf(5)))
        assertThat(coordinates[1][0]).containsExactly(
            GeoJsonPosition(20.0, 20.0),
            GeoJsonPosition(22.0, 20.0),
            GeoJsonPosition(22.0, 22.0),
            GeoJsonPosition(20.0, 22.0),
            GeoJsonPosition(20.0, 20.0),
        )
    }
})
