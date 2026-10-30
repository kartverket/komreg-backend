package no.kartverket.komreg.regulering.api.server

import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.OpenApiDocSource.Routing
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import jakarta.xml.ws.BindingProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Semaphore
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.kartverket.komreg.regulering.api.server.domain.CadestralUnit
import no.kartverket.komreg.regulering.api.server.domain.MatrikkelApiMapper
import no.kartverket.komreg.regulering.api.server.domain.MunicipalityNumber
import no.kartverket.komreg.regulering.api.server.domain.ParcelId
import no.kartverket.komreg.regulering.api.server.persistence.ReformRepository
import no.kartverket.komreg.regulering.api.server.service.matrikkelapi.DefaultMatrikkelApiMunicipalityNumbers
import no.kartverket.komreg.routes.Regulering
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleIdList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelContext
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Arc
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Polyline
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.koder.KoordinatsystemKodeId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.MatrikkelenhetId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Teig
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.TeigId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.TeigIdList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Teiggrense
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.TeiggrenseId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Teiggrensepunkt
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.TeiggrensepunktId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.kommune.KommuneServiceWS
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.matrikkelenhet.MatrikkelenhetService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.matrikkelenhet.MatrikkelenhetServiceWS
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.store.StoreService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.store.StoreServiceWS
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.teig.TeigService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.teig.TeigServiceWS
import org.apache.cxf.frontend.ClientProxy
import org.apache.cxf.transport.http.HTTPConduit
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.dsl.addFeature
import org.maplibre.spatialk.geojson.dsl.addPoint
import org.maplibre.spatialk.geojson.dsl.addRing
import org.maplibre.spatialk.geojson.dsl.buildFeatureCollection
import org.maplibre.spatialk.geojson.dsl.buildPolygon
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.Executors
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Point as WSPoint
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Matrikkelenhet as WSMatrikkelenhet

private val log = LoggerFactory.getLogger(Application::routes::class.java.enclosingClass)

@OptIn(ExperimentalKtorApi::class) // OpenAPI io.ktor.server.routing.openapi.DescribeRouteKt.describe
fun Application.routes(deploymentConfig: ApplicationConfig, appJson: Json, reforms: ReformRepository) {
    routing {
        swaggerUI(path = "/openapi") {
            info = OpenApiInfo("KomReg Regulering API", "0.1.0")
            remotePath = "openapi.json"
            source = Routing(
                routes = { descendants().filterNot { it.path.startsWith("/readyz") || it.path.startsWith("/livez") } }
            )
        }
        get("/greet") {
            log.info("Received request for /greet")
            call.respondText("Hello, World!")
            log.info("Responded to /greet")
        }
        get("/reform") {
            call.respond(reforms.summaries.toList())
        }
        get("/reform/part/{partId}/data") {
            val partId = call.parameters["partId"]
            call.respond(Regulering("partId", partId ?: "null", LocalDate(2020, 1, 1), emptyList()))
        }.describe {
            summary = "Bla bla bla"
            description = "Ja, ukeblad"
            parameters {
                path("partId") {
                    description = "The ID of the part to retrieve data for"
                    required = true
                    schema = jsonSchema<Int>()
                }
            }
        }
        matrikkelApiRoutes(deploymentConfig)
    }
}

fun Route.matrikkelApiRoutes(deploymentConfig: ApplicationConfig) {
    val server = URI.create(deploymentConfig.property("app.matrikkel.api.url").getString()).normalize().toString()
    val user = deploymentConfig.property("app.matrikkel.api.user").getString()
    val password = deploymentConfig.property("app.matrikkel.api.password").getString()
    fun <T : Any> T.configure(serviceName: String): T {
        (this as BindingProvider).apply {
            requestContext[BindingProvider.ENDPOINT_ADDRESS_PROPERTY] =
                "$server/$serviceName"
            requestContext[BindingProvider.USERNAME_PROPERTY] = user
            requestContext[BindingProvider.PASSWORD_PROPERTY] = password
            // Are JAX-WS client proxies thread safe?
            // https://cxf.apache.org/faq
            requestContext["thread.local.request.context"] = "true"
            val conduit = (ClientProxy.getClient(this).conduit as HTTPConduit)
            conduit.client = HTTPClientPolicy().apply {
                connectionTimeout = 3_000L
                receiveTimeout = 10_000L
            }
        }
        return this
    }

    val ctx = MatrikkelContext(
        "nb_NO",
        false,
        KoordinatsystemKodeId(11L),
        "4.27.2",
        "komreg-regulering-api",
        null
    )

    val matrikkelenhetService: MatrikkelenhetService
    val teigService: TeigService
    val storeService: StoreService
    val mapper: MatrikkelApiMapper
    runBlocking {
        withContext(Dispatchers.IO) {
            val kommuneServiceDeferred = async {
                KommuneServiceWS().kommuneServicePort.configure("KommuneServiceWS")
            }
            val storeServiceDeferred = async {
                StoreServiceWS().storeServicePort.configure("StoreServiceWS")
            }
            val matrikkelenhetServiceDeferred = async {
                MatrikkelenhetServiceWS().matrikkelenhetServicePort.configure("MatrikkelenhetServiceWS")
            }
            val teigServiceDeferred = async {
                TeigServiceWS().teigServicePort.configure("TeigServiceWS")
            }
            mapper = MatrikkelApiMapper(
                DefaultMatrikkelApiMunicipalityNumbers(
                    kommuneServiceDeferred.await(),
                    storeServiceDeferred.await(),
                    MunicipalityNumber.DefaultFactory,
                    ctx
                )
            )
            matrikkelenhetService = matrikkelenhetServiceDeferred.await()
            teigService = teigServiceDeferred.await()
            storeService = storeServiceDeferred.await()
        }
    }

    val executor = Executors.newVirtualThreadPerTaskExecutor()
    application.monitor.subscribe(ApplicationStopped) {
        log.info("Stopping matrikkel API services...")
        executor.shutdown()
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)
    }
    val dispatcher = executor.asCoroutineDispatcher()
    val semaphore = Semaphore(10)

    /**
     * Finn teigen og tilhørende matrikkelenheter som dekker et punkt (easting, northing) i UTM-sone 33.
     *
     */
    get("/point/cadestral_unit") {
        if (!semaphore.tryAcquire()) {
            call.respondText(
                text = "Too many concurrent requests, please try again later",
                status = io.ktor.http.HttpStatusCode.ServiceUnavailable
            )
            return@get
        }
        try {
            val easting = call.queryParameters["easting"]?.toDoubleOrNull()?.takeIf { it.isFinite() }
            val northing = call.queryParameters["northing"]?.toDoubleOrNull()?.takeIf { it.isFinite() }
            if (easting == null || northing == null) {
                call.respondText(
                    text = "Invalid or missing easting/northing parameters",
                    status = io.ktor.http.HttpStatusCode.BadRequest
                )
                return@get
            }

            withContext(dispatcher) {
                val wsParcelId =
                    teigService.findTeigerForPunkt(
                        WSPoint(easting, northing, null),
                        ctx
                    )?.item?.reduceOrNull { acc, teigId ->
                        check(acc == teigId); acc
                    }
                if (wsParcelId == null) {
                    call.respondText(
                        text = "No parcel found covering point ($easting, $northing)",
                        status = io.ktor.http.HttpStatusCode.NotFound
                    )
                    return@withContext
                }

                val wsRemainingCadestralUnitIds = ArrayDeque<MatrikkelenhetId>()
                val wsSeenCadestralUnitIds = hashSetOf<MatrikkelenhetId>()
                val wsVisitedParcelIds = hashSetOf<TeigId>()
                val wsCadestralUnits = mutableListOf<WSMatrikkelenhet>()

                for (id in matrikkelenhetService.findMatrikkelenheterForTeig(wsParcelId, ctx).item) {
                    if (wsSeenCadestralUnitIds.add(id)) {
                        wsRemainingCadestralUnitIds.addLast(id)
                    }
                }

                while (wsRemainingCadestralUnitIds.isNotEmpty()) {
                    val batchIds = wsRemainingCadestralUnitIds.toList()
                    wsRemainingCadestralUnitIds.clear()

                    val wsNewCadestralUnits = storeService
                        .getObjects(MatrikkelBubbleIdList(batchIds), ctx)
                        .item
                        .map { it as WSMatrikkelenhet }
                    wsCadestralUnits.addAll(wsNewCadestralUnits)

                    val wsNewParcelIds = wsNewCadestralUnits
                        .asSequence()
                        .flatMap { it.teigerForMatrikkelenhet.item.asSequence() }
                        .map { it.teigId }
                        .filter { wsVisitedParcelIds.add(it) }
                        .toList()

                    if (wsNewParcelIds.isNotEmpty()) {
                        matrikkelenhetService
                            .findMatrikkelenheterForTeiger(TeigIdList(wsNewParcelIds), ctx)
                            .entry
                            .asSequence()
                            .flatMap { it.value.item.asSequence() }
                            .forEach { id ->
                                if (wsSeenCadestralUnitIds.add(id)) {
                                    wsRemainingCadestralUnitIds.addLast(id)
                                }
                            }
                    }
                }

                val teiger = storeService
                    .getObjects(MatrikkelBubbleIdList(wsVisitedParcelIds.distinct().toList()), ctx)
                    .item.asSequence()
                    .map { it as Teig }.associateByTo(hashMapOf()) { it.id as TeigId }

                val exteriorCds = teiger.values.flatMap { it.flate.exterior.curveDirections.item }
                val interiorCds = teiger.values.flatMap { it.flate.interior.item }.map { it.curveDirections.item }
                val grenselinjeIds = (exteriorCds + interiorCds.flatten()).distinctBy { it.id }.map { it.grenselinjeId }

                val grenselinjer = storeService.getObjects(MatrikkelBubbleIdList(grenselinjeIds), ctx)
                    .item
                    .map { it as Teiggrense }
                    .associateBy { it.id as TeiggrenseId }

                val grensepunktIds = grenselinjer
                    .values
                    .asSequence()
                    .flatMap {
                        sequenceOf(it.kurve.startpunktId, it.kurve.endpunktId)
                    }
                    .distinct()
                    .toList()

                val grensepunkter = storeService.getObjects(MatrikkelBubbleIdList(grensepunktIds), ctx)
                    .item
                    .asSequence()
                    .map { it as Teiggrensepunkt }
                    .associateBy { it.id as TeiggrensepunktId }

                exteriorCds
                    .map {
                        grenselinjer[it.grenselinjeId]
                    }

                val features = buildFeatureCollection {
                    for (teig in teiger.values) {
                        val teigPoly = buildPolygon {
                            addRing {
                                for (cd in teig.flate.exterior.curveDirections.item) {
                                    val grenseselinje = grenselinjer[cd.grenselinjeId]!!
                                    val startpunkt = grensepunkter[grenseselinje.kurve.startpunktId]!!
                                    val endpunkt = grensepunkter[grenseselinje.kurve.endpunktId]!!
                                    val midpoints = when (val kurve = grenseselinje.kurve) {
                                        is Arc -> listOf(
                                            org.maplibre.spatialk.geojson.Position(
                                                kurve.buepunkt.x,
                                                kurve.buepunkt.y
                                            )
                                        )

                                        is Polyline -> kurve.kurvepunkter.item.map {
                                            org.maplibre.spatialk.geojson.Position(
                                                it.x,
                                                it.y
                                            )
                                        }

                                        else -> throw AssertionError("Unexpected curve type: ${grenseselinje.kurve::class.simpleName}")
                                    }

                                    if (cd.isSigned) {
                                        addPoint(
                                            org.maplibre.spatialk.geojson.Position(
                                                startpunkt.posisjon.x,
                                                startpunkt.posisjon.x
                                            )
                                        )
                                        midpoints.forEach { add(it) }
                                        addPoint(
                                            org.maplibre.spatialk.geojson.Position(
                                                endpunkt.posisjon.x,
                                                endpunkt.posisjon.x
                                            )
                                        )
                                    } else {
                                        addPoint(
                                            org.maplibre.spatialk.geojson.Position(
                                                endpunkt.posisjon.x,
                                                endpunkt.posisjon.x
                                            )
                                        )
                                        midpoints.reversed().forEach { add(it) }
                                        addPoint(
                                            org.maplibre.spatialk.geojson.Position(
                                                startpunkt.posisjon.x,
                                                startpunkt.posisjon.x
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        addFeature(geometry = teigPoly) {
                            geometry = teigPoly
                            properties = buildJsonObject {
                                put("parcelId", teig.id.value)
                            }
                        }
                    }
                }

                with(mapper) {
                    val cadestralUnits = wsCadestralUnits.map { it.toDomain() }
                    call.respond(CadestralUnitCoveringPointResult(wsParcelId.toDomain(), cadestralUnits, features))
                }
            }
        } finally {
            semaphore.release()
        }

    }
}

@Serializable
data class CadestralUnitCoveringPointResult(
    val coveringParcelId: ParcelId,
    val cadestralUnits: Collection<CadestralUnit>,
    val features: FeatureCollection<Polygon, JsonObject?>,
)
