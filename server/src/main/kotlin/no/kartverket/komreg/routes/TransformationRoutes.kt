package no.kartverket.komreg.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.services.transformEntities
import no.kartverket.komreg.services.transformStatuses

fun Application.transformationRoutes(transformationRepo: TransformationRepo, kjoringRepo: KjoringRepo) {
    routing {
        route("/run") {
            post {
                val regulering: Regulering = call.receive()

                call.application.log.info(regulering.toString())
                call.application.log.info(regulering.toReguleringsinput().toString())

                val kjoringId = kjoringRepo.insertAndRetrieveKjoringId(regulering.id)
                if (kjoringId != null) {
                    val reguleringsinput = regulering.toReguleringsinput()

                    transformEntities(reguleringsinput, kjoringId, transformationRepo, kjoringRepo)

                    call.respond("OK")
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to insert into kjoring table.")
                }
            }
        }

        route("/transform/status") {
            get {
                call.respond(transformStatuses)
            }
        }
    }
}
