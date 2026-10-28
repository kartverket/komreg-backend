package no.kartverket.komreg.regulering.api.server

import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.OpenApiDocSource.Routing
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.path
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi
import jakarta.xml.ws.BindingProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.komreg.regulering.api.server.domain.CadestralUnit
import no.kartverket.komreg.regulering.api.server.domain.CadestralUnitId
import no.kartverket.komreg.regulering.api.server.domain.MunicipalityNumber
import no.kartverket.komreg.regulering.api.server.domain.ParcelId
import no.kartverket.komreg.regulering.api.server.persistence.ReformRepository
import no.kartverket.komreg.regulering.api.server.service.matrikkelapi.DefaultMatrikkelApiMunicipalityNumbers
import no.kartverket.komreg.regulering.api.server.service.matrikkelapi.MatrikkelAPI
import no.kartverket.komreg.routes.Regulering
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.kommune.KommuneServiceWS
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.matrikkelenhet.MatrikkelenhetServiceWS
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.store.StoreServiceWS
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.teig.TeigServiceWS
import org.slf4j.LoggerFactory
import java.net.URI
import kotlin.collections.asSequence
import kotlin.collections.set

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

@Serializable
data class Matrikkelenhet(
    val id: Long,
    val kommunenummer: String,
    val gaardsnummer: Int,
    val bruksnummer: Int,
    val festenummer: Int,
    val seksjonsnummer: Int
)

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
        }
        return this
    }

    val matrikkelApi = runBlocking {
        withContext(Dispatchers.IO) {
            val kommuneService = async { KommuneServiceWS().kommuneServicePort.configure("KommuneServiceWS") }
            val storeService = async { StoreServiceWS().storeServicePort.configure("StoreServiceWS") }
            val matrikkelenhetService = async { MatrikkelenhetServiceWS().matrikkelenhetServicePort.configure("MatrikkelenhetServiceWS") }
            val teigService = async { TeigServiceWS().teigServicePort.configure("TeigServiceWS") }
            val municipalities =
                DefaultMatrikkelApiMunicipalityNumbers(kommuneService.await(), storeService.await(), MunicipalityNumber.DefaultFactory)
            MatrikkelAPI(
                storeService.await(), matrikkelenhetService.await(), teigService.await(),
                municipalities
            )
        }
    }

    get("/point/{x}/{y}/cadestralunit") {
        val x = call.parameters["x"]!!.toDouble()
        val y = call.parameters["y"]!!.toDouble()

        val parcelId = matrikkelApi.findParcelIdCoveringPoint(x, y)
        if (parcelId == null) {
            call.respondText("No parcel found covering point ($x, $y)", status = io.ktor.http.HttpStatusCode.NotFound)
            return@get
        }
        val cadestralUnitIds = matrikkelApi.findCadestralUnitIdsForParcels(setOf(parcelId)).toMap(HashMap())
        val remainingCadestralUnits = cadestralUnitIds.values.flatten().toCollection(ArrayDeque())
        val visitedCadestralUnit = hashSetOf<CadestralUnitId>()
        val visitedParcelIds = hashSetOf(parcelId)
        val cadestralUnits = mutableListOf<CadestralUnit>()
        while (remainingCadestralUnits.isNotEmpty()) {
            val newCadestralUnits = matrikkelApi.get(remainingCadestralUnits)
            cadestralUnits.addAll(newCadestralUnits)
            visitedCadestralUnit.addAll(remainingCadestralUnits)
            remainingCadestralUnits.clear()
            val newParcelIds = newCadestralUnits.asSequence().flatMap { sequenceOf(it.mainParcelId) + it.otherParcelIds }.filterNot { visitedParcelIds.contains(it) }.toHashSet()
            if (newParcelIds.isNotEmpty()) {
                remainingCadestralUnits += matrikkelApi.findCadestralUnitIdsForParcels(newParcelIds).asSequence().flatMap { it.value.asSequence() }.filterNot { visitedCadestralUnit.contains(it) }
            }
        }
        call.respond(CadestralUnitCoveringPointResult(parcelId, cadestralUnits))
    }
}

@Serializable
data class CadestralUnitCoveringPointResult(
    val coveringParcelId: ParcelId,
    val cadestralUnits: Collection<CadestralUnit>
)
