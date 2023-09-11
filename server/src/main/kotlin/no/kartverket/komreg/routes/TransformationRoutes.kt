package no.kartverket.komreg.routes

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.kartverket.komreg.Regulering
import no.kartverket.komreg.transformation.transformEntities
import no.kartverket.komreg.transformation.transformStatuses

fun Application.transformationRoutes() {
    routing {
        route("/run") {
            post {
                val regulering: Regulering = call.receive()
                val reguleringsinput = regulering.toReguleringsinput()

                call.application.log.info(regulering.toString())
                call.application.log.info(reguleringsinput.toString())

                transformEntities(reguleringsinput)

                call.respond("OK")
            }
        }

        route("/transform/status") {
            get {
                call.respond(transformStatuses)
            }
        }
    }
}
