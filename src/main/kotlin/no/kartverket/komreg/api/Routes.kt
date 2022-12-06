package no.kartverket.komreg.api

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.kartverket.komreg.domain.EntityData
import no.kartverket.komreg.executeRun
import no.kartverket.komreg.transformation.AddGardsnummerRule
import no.kartverket.komreg.transformation.Transform
import no.kartverket.komreg.transformation.TransformFunc

fun Application.configureRouting() {
    routing {
        route("/run") {
            get {
                val rules: List<TransformFunc<EntityData>> = listOf(
                    AddGardsnummerRule(2..10, 50),
                    AddGardsnummerRule(426..426, 50),
                    AddGardsnummerRule(426 + 50..426 + 50, 30)
                )
                call.respond(executeRun(rules).toJson())
            }
            post {
                val ruleset = call.receive<Ruleset>()
                val rules: List<TransformFunc<EntityData>> = ruleset.gaardsnummer.map {
                    AddGardsnummerRule(it.start..it.end, it.increase)
                }
                call.respond(executeRun(rules).toJson())
            }
        }
    }
}

fun List<Transform<EntityData>>.toJson(): Result {
    val errors = this
        .filterIsInstance<Transform.Transformed<*>>()
        .filter { it.entity.data.log.size > 0 }
        .map { ErrorsJson(it.entity.toString(), it.entity.data.log.map { l -> l.toString() }) }

    val transforms = this
        .filterIsInstance<Transform.Transformed<*>>()
        .filter { it.entity.data.log.size == 0 }
        .map {
            TransformedJson(
                it.transformation.description,
                it.entity.data.toString()
            )
        }

    return Result(transforms, errors)
}
