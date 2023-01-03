package no.kartverket.komreg.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
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
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.serialization.sendSerializedBase
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
import java.nio.charset.Charset
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

val randomMessages = listOf("Feil ved transformasjon", "Dette gikk ikke", "Huff da")

@Serializable
sealed class FrontendMessage {
    @Serializable
    data class Error(val errorMsg: String, val data: String, val value: Int) : FrontendMessage()

    @Serializable
    data class Success(val data: String, val oldValue: Int, val newValue: Int) : FrontendMessage()
}

fun Application.configureRouting(wsclient: HttpClient) {
    val connections = Collections.synchronizedSet<WebsocketBroadcast.WebsocketConnection?>(LinkedHashSet())
    val websocketBroadcast = WebsocketBroadcast(wsclient, connections)

    routing {
        route("/run") {
            get {
                println("Calling get method")
                val rules: List<TransformFunc<EntityData>> = listOf(
                    AddGardsnummerRule(2..10, 50),
                    AddGardsnummerRule(426..426, 50),
                    AddGardsnummerRule(426 + 50..426 + 50, 30)
                )
                call.respond(executeRun(rules, websocketBroadcast).toJson())
            }
            post {
                val ruleset = call.receive<Ruleset>()
                val rules: List<TransformFunc<EntityData>> = ruleset.gaardsnummer.map {
                    AddGardsnummerRule(it.start..it.end, it.increase)
                }
                call.respond(FrontendMessage.Success("Kjøring startet", 2, 2))
                launch { executeRun(rules, websocketBroadcast).toJson() }
            }
        }
        webSocket("/feed") {
            val thisConnection = WebsocketBroadcast.WebsocketConnection(this)
            println("Adding $thisConnection")
            connections += thisConnection

            try {
                for (frame in incoming) {
                    println("Incomming")
                }
            } catch (e: ClosedReceiveChannelException) {
                println("Ouch")
            } catch (e: Exception) {
                println("Ouch2")
            } finally {
                println("Removing $thisConnection")
                connections -= thisConnection
            }
        }

        webSocket("/hei") {
            val thisConnection = WebsocketBroadcast.WebsocketConnection(this)
            println("Adding $thisConnection")

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
            send(Frame.Close())
            println("Removing $thisConnection")
            connections -= thisConnection
        }
    }
}

data class WebsocketBroadcast(
    val httpClient: HttpClient,
    val clients: MutableSet<WebsocketConnection>,
) {

    class WebsocketConnection(val session: DefaultWebSocketSession) {
        val id = lastId.getAndIncrement()

        companion object {
            val lastId = AtomicInteger(0)
        }
    }

    val converter = KotlinxWebsocketSerializationConverter(Json)
    var connection: DefaultClientWebSocketSession? = null

    suspend fun connect() {
        httpClient.webSocket(method = HttpMethod.Get, host = "localhost", port = 8080, path = "/feed") {
            connection = this
        }
    }

    suspend fun sendText(msg: String) {
        clients.forEach {
            it.session.send(Frame.Text(msg))
        }
    }

    suspend inline fun <reified T> sendSerialized(data: T) {
        clients.forEach {
            it.session.sendSerializedBase(
                data,
                converter,
                Charset.defaultCharset()
            )
        }
    }

    suspend fun close() {
        clients.forEach {
            it.session.send(Frame.Close())
        }
        connection?.send(Frame.Close())
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
