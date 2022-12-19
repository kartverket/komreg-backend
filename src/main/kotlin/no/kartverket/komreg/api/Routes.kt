package no.kartverket.komreg.api

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.delay
import no.kartverket.komreg.domain.EntityData
import no.kartverket.komreg.executeRun
import no.kartverket.komreg.transformation.AddGardsnummerRule
import no.kartverket.komreg.transformation.Transform
import no.kartverket.komreg.transformation.TransformFunc

suspend fun WebSocketServerSession.lol() {
    send(Frame.Text("Hoi"))
}

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
                executeRun(rules).toJson()
                call.respond("Kjøring startet")
            }
        }
        webSocket("current") {
        }
        webSocket("/hei") {
            send(Frame.Text("Starter: Hoi"))
            var currentId = 0
            while (currentId < 30) {
                currentId++
                delay(3000)
                send(Frame.Text("Løpende endring: Oh Hoi $currentId"))
            }
            send(Frame.Text("Slutter"))
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
