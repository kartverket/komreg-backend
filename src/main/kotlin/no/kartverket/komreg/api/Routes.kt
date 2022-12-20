package no.kartverket.komreg.api

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import no.kartverket.komreg.domain.EntityData
import no.kartverket.komreg.domain.Grunneiendom
import no.kartverket.komreg.executeRun
import no.kartverket.komreg.experimental.GeneratedId
import no.kartverket.komreg.experimental.Valid
import no.kartverket.komreg.experimental.Validation
import no.kartverket.komreg.experimental.VirtualEntity
import no.kartverket.komreg.transformation.AddGardsnummerRule
import no.kartverket.komreg.transformation.Transform
import no.kartverket.komreg.transformation.TransformFunc
import java.util.LinkedList
import java.util.Queue
import kotlin.random.Random

val randomMessages = listOf("Feil ved transformasjon", "Dette gikk ikke", "Huff da")

@Serializable
sealed class FrontendMessage {
    @Serializable
    data class Error(val errorMsg: String, val data: String, val value: Int) : FrontendMessage()

    @Serializable
    data class Success(val data: String, val oldValue: Int, val newValue: Int) : FrontendMessage()
}

fun Application.configureRouting() {
    routing {
        val messageQueue: Queue<String> = LinkedList(listOf("a", "b", "c"))
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
                messageQueue.add("Hei")
                val ruleset = call.receive<Ruleset>()
                val rules: List<TransformFunc<EntityData>> = ruleset.gaardsnummer.map {
                    AddGardsnummerRule(it.start..it.end, it.increase)
                }
                executeRun(rules).toJson()
                call.respond("Kjøring startet")
            }
        }
        webSocket("/hei") {
            val data = Grunneiendom(10, 10, 10, emptySet(), emptySet())
            val validated = VirtualEntity(GeneratedId.invoke(), Valid(data) as Validation<Grunneiendom>)
            val transformed = Transform.Transformed(validated, AddGardsnummerRule(2..10, 50))
            sendSerialized(
                listOf(
                    FrontendMessage.Success(transformed.toString(), Random.nextInt(), Random.nextInt()),
                    FrontendMessage.Success(transformed.toString(), Random.nextInt(), Random.nextInt()),
                    FrontendMessage.Success(transformed.toString(), Random.nextInt(), Random.nextInt()),
                    FrontendMessage.Error(randomMessages.random(), transformed.toString(), Random.nextInt()),
                    FrontendMessage.Error(randomMessages.random(), transformed.toString(), Random.nextInt())
                )
            )
            var currentId = 0
            while (currentId < 30) {
                currentId++
                delay(3000)
                if (Random.nextFloat() > 0.5f) {
                    sendSerialized(FrontendMessage.Success(transformed.toString(), Random.nextInt(), Random.nextInt()))
                } else {
                    sendSerialized(
                        FrontendMessage.Error(
                            randomMessages.random(),
                            transformed.toString(),
                            Random.nextInt()
                        )
                    )
                }
            }
            send(Frame.Close())
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
