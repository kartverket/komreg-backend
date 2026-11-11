package no.kartverket.komreg.regulering.api.server

import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.OpenApiDocSource.Routing
import jakarta.xml.ws.BindingProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.kartverket.komreg.regulering.api.server.domain.MunicipalityId
import no.kartverket.komreg.regulering.reform.domain.type.NumberingScheme
import no.kartverket.komreg.regulering.api.server.persistence.ReformRepository
import no.kartverket.komreg.regulering.api.server.route.regulering.reguleringer
import no.kartverket.komreg.regulering.api.server.service.matrikkelapi.CadastralUnitFeatures
import no.kartverket.komreg.regulering.api.server.service.matrikkelapi.EPSG_25833_CRS
import no.kartverket.komreg.regulering.api.server.service.matrikkelapi.MatrikkelApiMapper
import no.kartverket.komreg.regulering.api.server.service.matrikkelapi.MatrikkelApiMunicipalityCache
import no.kartverket.komreg.regulering.api.server.service.matrikkelapi.MatrikkelApiMunicipalityDataSource
import no.kartverket.komreg.regulering.api.server.service.matrikkelapi.MatrikkelApiMunicipalityNumbers
import no.kartverket.komreg.regulering.api.server.service.matrikkelapi.getObjectsChunked
import no.kartverket.komreg.regulering.api.server.service.matrikkelapi.teigerInKrets
import no.kartverket.komreg.regulering.api.server.service.matrikkelapi.toGeoJson
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelContext
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.adresse.Grunnkrets
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.adresse.GrunnkretsId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.adresse.Kretsflate
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.SelectionPolygon
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.koder.KoordinatsystemKodeId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Teig
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.TeigId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.adresse.AdresseService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.adresse.AdresseServiceWS
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.bygning.BygningService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.bygning.BygningServiceWS
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
import org.maplibre.spatialk.geojson.dsl.buildFeature
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.Executors
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Point as WSPoint

private val log = LoggerFactory.getLogger(Application::routes::class.java.enclosingClass)

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
        matrikkelApiRoutes(deploymentConfig, appJson)
        reguleringer(appJson, reforms)
    }
}

fun Route.matrikkelApiRoutes(deploymentConfig: ApplicationConfig, appJson: Json) {
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

    val utm33 = KoordinatsystemKodeId(11L)
    val ctx = MatrikkelContext(
        "nb_NO",
        false,
        utm33,
        "4.27.2",
        "komreg-regulering-api",
        null
    )

    val matrikkelenhetService: MatrikkelenhetService
    val teigService: TeigService
    val storeService: StoreService
    val bygningService: BygningService
    val adresseService: AdresseService
    val mapper: MatrikkelApiMapper
    val municipalityNumbers: MatrikkelApiMunicipalityNumbers
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
            val bygningServiceDeferred = async {
                BygningServiceWS().bygningServicePort.configure("BygningServiceWS")
            }
            val adresseServiceDeferred = async {
                AdresseServiceWS().adresseServicePort.configure("AdresseServiceWS")
            }
            storeService = storeServiceDeferred.await()
            municipalityNumbers = MatrikkelApiMunicipalityCache(
                MatrikkelApiMunicipalityDataSource(kommuneServiceDeferred.await(), storeService, ctx),
                NumberingScheme.fromName(deploymentConfig.propertyOrNull("app.numberingScheme")?.getString()),
            )
            mapper = MatrikkelApiMapper(
                municipalityNumbers
            )
            matrikkelenhetService = matrikkelenhetServiceDeferred.await()
            teigService = teigServiceDeferred.await()
            bygningService = bygningServiceDeferred.await()
            adresseService = adresseServiceDeferred.await()
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
    val features = CadastralUnitFeatures(
        matrikkelenhetService, teigService, storeService, bygningService, adresseService, ctx, mapper, appJson
    )

    /** Kjører [block] mot MatrikkelAPI. */
    suspend fun RoutingContext.withMatrikkelApi(block: suspend () -> Unit) {
        if (!semaphore.tryAcquire()) {
            call.respondText(
                text = "Too many concurrent requests, please try again later",
                status = HttpStatusCode.ServiceUnavailable
            )
            return
        }
        try {
            withContext(dispatcher) { block() }
        } finally {
            semaphore.release()
        }
    }

    /** Punktet (easting, northing) i UTM-sone 33 fra query-parameterne, eller 400 hvis det mangler. */
    suspend fun RoutingContext.pointOrBadRequest(): WSPoint? {
        val easting = call.queryParameters["easting"]?.toDoubleOrNull()?.takeIf { it.isFinite() }
        val northing = call.queryParameters["northing"]?.toDoubleOrNull()?.takeIf { it.isFinite() }
        if (easting == null || northing == null) {
            call.respondText(
                text = "Invalid or missing easting/northing parameters",
                status = HttpStatusCode.BadRequest
            )
            return null
        }
        return WSPoint(easting, northing, null)
    }

    /**
     * Finn teigen og tilhørende matrikkelenheter som dekker et punkt (easting, northing) i UTM-sone 33.
     */
    get("/point/cadestral_unit") {
        withMatrikkelApi {
            val point = pointOrBadRequest() ?: return@withMatrikkelApi

            // Finn teigen som dekker punktet (easting, northing)
            val wsParcelId =
                teigService.findTeigerForPunkt(point, ctx)?.item?.reduceOrNull { acc, teigId ->
                    check(acc == teigId); acc
                }
            if (wsParcelId == null) {
                call.respondText(
                    text = "No parcel found covering point (${point.x}, ${point.y})",
                    status = HttpStatusCode.NotFound
                )
                return@withMatrikkelApi
            }

            call.respond(features.collect(listOf(wsParcelId)) { put("coveringParcel", it == wsParcelId) })
        }
    }

    /**
     * Finn grunnkretsen som dekker et punkt (easting, northing) i UTM-sone 33, med omriss. Lar
     * klienten vise kretsen før teigene hentes med /krets/{id}/cadastral_unit.
     */
    get("/point/krets") {
        withMatrikkelApi {
            val point = pointOrBadRequest() ?: return@withMatrikkelApi

            // Punktet ligger i én krets per kretstype (grunnkrets, sokn, valgkrets …)
            val krets = adresseService.findKretserForPunkt(point, ctx)?.item.orEmpty()
                .let { storeService.getObjectsChunked(it, ctx) }
                .filterIsInstance<Grunnkrets>()
                .firstOrNull()
            if (krets == null) {
                call.respondText(
                    text = "No grunnkrets found covering point (${point.x}, ${point.y})",
                    status = HttpStatusCode.NotFound
                )
                return@withMatrikkelApi
            }
            val kretsflate = storeService.getObject(krets.kretsflateId, ctx) as Kretsflate

            call.respond(buildFeature(kretsflate.geometri.toGeoJson()) {
                setId(krets.id.value)
                properties = buildJsonObject {
                    put("municipalityNumber", municipalityNumbers[MunicipalityId(krets.kommuneIds.item.single().value)].value)
                    put("kretsnummer", krets.kretsnummer)
                    put("kretsnavn", krets.kretsnavn)
                }
                foreignMembers = buildJsonObject {
                    put("featureType", "Krets")
                    put("crs", EPSG_25833_CRS)
                }
            })
        }
    }

    /**
     * Finn teigene i en grunnkrets, med tilhørende matrikkelenheter og adresser. En teig hører til
     * kretsen som representasjonspunktet dens ligger i.
     *
     * Matrikkelenheter flyttes samlet, så teiger utenfor kretsen blir med når de deler
     * matrikkelenhet med en teig innenfor. De er merket med `inKrets: false`.
     */
    get("/krets/{id}/cadastral_unit") {
        withMatrikkelApi {
            val kretsId = call.parameters["id"]?.toLongOrNull()
            if (kretsId == null) {
                call.respondText(text = "Invalid krets id", status = HttpStatusCode.BadRequest)
                return@withMatrikkelApi
            }
            val krets = storeService.getObject(GrunnkretsId(kretsId), ctx) as Grunnkrets
            val kretsflate = (storeService.getObject(krets.kretsflateId, ctx) as Kretsflate).geometri

            // Søket tar ett polygon, og en krets kan ha flere deler (øyer, enklaver)
            val candidateIds = kretsflate.polygoner.item
                .flatMap { teigService.findTeigerForSelectionPolygon(SelectionPolygon(it, utm33), ctx)?.item.orEmpty() }
                .distinct()
            val candidates = storeService.getObjectsChunked(candidateIds, ctx).map { it as Teig }
            val parcelIdsInKrets = teigerInKrets(candidates, kretsflate).mapTo(hashSetOf()) { it.id as TeigId }
            log.info(
                "Krets {}: {} candidate parcels, {} without representasjonspunkt, {} in krets",
                kretsId,
                candidates.size,
                candidates.count { it.representasjonspunkt?.position == null },
                parcelIdsInKrets.size
            )

            call.respond(features.collect(parcelIdsInKrets) { put("inKrets", it in parcelIdsInKrets) })
        }
    }
}
