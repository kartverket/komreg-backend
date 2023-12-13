package no.kartverket.komreg.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.kartverket.komreg.KrAppBootContextImpl
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.services.StedsnavnService

fun Application.stedsnavnRoutes(
    reguleringRepo: ReguleringRepo,
    kjoringRepo: KjoringRepo,
    transformationRepo: TransformationRepo
) {
    val stedsnavnService = StedsnavnService(reguleringRepo, kjoringRepo, transformationRepo)

    routing {
        get("/stedsnavn/json/{kjoring}") {
            val kjoringId = call.parameters["kjoring"]!!.toInt()

            val generator = stedsnavnService.createSsrJsonWriter(KrAppBootContextImpl, kjoringId)
            if (generator != null) {
                call.respondTextWriter(
                    ContentType.Application.Json,
                ) {
                    generator(this)
                }
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    "Kjøring not found",
                )
            }
        }

        get("/stedsnavn/parameter/{kjoring}") {
            val kjoringId = call.parameters["kjoring"]!!.toInt()

            val generator = stedsnavnService.createParameterWriter(kjoringId)
            if (generator != null) {
                call.respondTextWriter(
                    ContentType.Text.Plain,
                ) {
                    generator(this)
                }
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    "Kjøring not found",
                )
            }
        }
    }
}
