package no.kartverket.komreg.routes

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun Application.internalRoutes(metricsRegistry: PrometheusMeterRegistry) {
    routing {
        route("/actuator/health") {
            get {
                call.respond("OK")
            }
        }

        route("/actuator/metrics") {
            get {
                call.respond(metricsRegistry.scrape())
            }
        }
    }
}
