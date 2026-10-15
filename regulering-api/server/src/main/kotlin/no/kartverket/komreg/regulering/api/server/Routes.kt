package no.kartverket.komreg.regulering.api.server

import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.OpenApiDocSource.Routing
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.path
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.regulering.api.server.persistence.ReformRepository
import no.kartverket.komreg.routes.Regulering
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(Application::routes::class.java.enclosingClass)

@OptIn(ExperimentalKtorApi::class) // OpenAPI io.ktor.server.routing.openapi.DescribeRouteKt.describe
fun Application.routes(reforms: ReformRepository) {
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
    }
}
