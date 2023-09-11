package no.kartverket.komreg.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.datetime.toJavaLocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.kartverket.komreg.Fylkesdeling
import no.kartverket.komreg.Regulering
import no.kartverket.komreg.env
import java.sql.Date
import java.sql.DriverManager

fun Application.reguleringRoutes() {
    val url = env["DB_KOMREG_JDBC_URL"]
    val user = env["DB_KOMREG_USERNAME"]
    val password = env["DB_KOMREG_PASSWORD"]

    routing {
        // Get all reguleringer
        route("/reguleringer") {
            get {
                val connection = DriverManager.getConnection(url, user, password)
                val statement = connection.prepareStatement("SELECT * FROM regulering")
                val resultSet = statement.executeQuery()

                val reguleringerList = mutableListOf<Regulering>()

                while (resultSet.next()) {
                    val reguleringJson = resultSet.getString("regulering")
                    val regulering = Json.decodeFromString<Regulering>(reguleringJson)
                    reguleringerList.add(regulering)
                }

                if (reguleringerList.isEmpty()) {
                    call.application.log.info("No reguleringer found.")
                }
                call.respond(reguleringerList)

                statement.close()
                connection.close()
            }
        }

        // Get regulering by id
        route("/reguleringer/{id}") {
            get {
                val regId = call.parameters["id"]

                val connection = DriverManager.getConnection(url, user, password)
                val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
                statement.setString(1, regId)
                val resultSet = statement.executeQuery()

                if (resultSet.next()) {
                    val reguleringJson = resultSet.getString("regulering")
                    val regulering = Json.decodeFromString<Regulering>(reguleringJson)
                    call.respond(regulering)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Regulering not found.")
                }
                statement.close()
                connection.close()
            }
        }

        // Create new regulering
        route("/reguleringer") {
            post {
                val regulering: Regulering = call.receive()

                val connection = DriverManager.getConnection(url, user, password)
                val statement = connection.prepareStatement(
                    "INSERT INTO regulering (id, regulering, ikrafttredelsesdato, opprettet, endret, opprettetav) VALUES (?, ?::jsonb, ?, now(), now(), ?)",
                )

                statement.setString(1, regulering.id)
                statement.setString(2, Json.encodeToString(regulering))
                statement.setDate(3, Date.valueOf(regulering.dato.toJavaLocalDate()))
                statement.setString(4, "system")

                statement.executeUpdate()

                call.respond(HttpStatusCode.OK, "Regulering JSON received and saved successfully.")
            }
        }

        // Modify (replace) existing regulering
        route("/reguleringer") {
            put {
                val regulering: Regulering = call.receive()

                val connection = DriverManager.getConnection(url, user, password)

                val checkStatement = connection.prepareStatement("SELECT count(id) FROM regulering WHERE id = ?")
                checkStatement.setString(1, regulering.id)
                val resultSet = checkStatement.executeQuery()
                resultSet.next()
                val count = resultSet.getInt(1)
                checkStatement.close()

                if (count == 0) {
                    call.respond(HttpStatusCode.NotFound, "Regulering with ID ${regulering.id} not found.")
                    connection.close()
                    return@put
                }

                val updateStatement = connection.prepareStatement(
                    "UPDATE regulering SET regulering = ?::jsonb, ikrafttredelsesdato = ?, endret = now(), opprettetav = ? WHERE ID = ?",
                )
                updateStatement.setString(1, Json.encodeToString(regulering))
                updateStatement.setDate(2, Date.valueOf(regulering.dato.toJavaLocalDate()))
                updateStatement.setString(3, "system")
                updateStatement.setString(4, regulering.id)

                updateStatement.executeUpdate()
                updateStatement.close()
                connection.close()

                call.respond(HttpStatusCode.OK, "Regulering with ID ${regulering.id} updated successfully.")
            }
        }

        // Delete regulering by id
        route("/reguleringer/{regId}") {
            delete {
                val regId = call.parameters["regId"]

                val connection = DriverManager.getConnection(url, user, password)
                val checkStatement = connection.prepareStatement("SELECT count(id) FROM regulering WHERE id = ?")
                checkStatement.setString(1, regId)
                val resultSet = checkStatement.executeQuery()
                resultSet.next()
                val count = resultSet.getInt(1)
                checkStatement.close()

                if (count == 0) {
                    connection.close()
                    call.respond(HttpStatusCode.NotFound, "Regulering with ID $regId not found.")
                    return@delete
                }

                val deleteStatement = connection.prepareStatement("DELETE FROM regulering WHERE id = ?")
                deleteStatement.setString(1, regId)
                deleteStatement.executeUpdate()
                deleteStatement.close()
                connection.close()

                call.respond(HttpStatusCode.OK, "Regulering with ID $regId deleted successfully.")
            }
        }

        // Get endring by id in regulering by id
        route("/reguleringer/{regId}/endringer/{endrId}") {
            get {
                val regId = call.parameters["regId"]
                val endrId = call.parameters["endrId"]

                val connection = DriverManager.getConnection(url, user, password)
                val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
                statement.setString(1, regId)
                val resultSet = statement.executeQuery()

                if (resultSet.next()) {
                    val reguleringJson = resultSet.getString("regulering")
                    val regulering = Json.decodeFromString<Regulering>(reguleringJson)

                    val endring = regulering.endringer.find { it.id == endrId }

                    if (endring != null) {
                        call.respond(endring)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Endring not found.")
                    }
                } else {
                    call.respond(HttpStatusCode.NotFound, "Regulering not found.")
                }

                statement.close()
                connection.close()
            }
        }

        // Get all endringer in regulering by id
        route("/reguleringer/{regId}/endringer") {
            get {
                val regId = call.parameters["regId"]

                val connection = DriverManager.getConnection(url, user, password)
                val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
                statement.setString(1, regId)
                val resultSet = statement.executeQuery()

                if (resultSet.next()) {
                    val reguleringJson = resultSet.getString("regulering")
                    val regulering = Json.decodeFromString<Regulering>(reguleringJson)

                    if (regulering.endringer.isNotEmpty()) {
                        call.respond(regulering.endringer)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "No Endringer found for the specified Regulering.")
                    }
                } else {
                    call.respond(HttpStatusCode.NotFound, "Regulering not found.")
                }

                statement.close()
                connection.close()
            }
        }

        // Create new endring in regulering by reguleringId
        route("/reguleringer/{regId}/endringer") {
            post {
                val regId = call.parameters["regId"]
                val endringJson: String = call.receiveText()
                val endring = Json.decodeFromString<Fylkesdeling>(endringJson)

                val connection = DriverManager.getConnection(url, user, password)
                val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
                statement.setString(1, regId)

                val resultSet = statement.executeQuery()
                if (resultSet.next()) {
                    val reguleringJson = resultSet.getString("regulering")
                    val regulering = Json.decodeFromString<Regulering>(reguleringJson)

                    val updatedEndringer = regulering.endringer + endring
                    val updatedRegulering = regulering.copy(endringer = updatedEndringer)

                    val updateStatement =
                        connection.prepareStatement("UPDATE regulering SET regulering = ?::jsonb WHERE ID = ?")
                    updateStatement.setString(1, Json.encodeToString(updatedRegulering))
                    updateStatement.setString(2, regId)
                    updateStatement.executeUpdate()

                    call.respond(HttpStatusCode.OK, "Endring added successfully.")
                } else {
                    call.respond(HttpStatusCode.NotFound, "Regulering not found.")
                }

                statement.close()
                connection.close()
            }
        }

        // Modify (replace) existing endring by id in regulering by id
        route("/reguleringer/{regId}/endringer/{endringId}") {
            put {
                val regId = call.parameters["regId"]
                val endringId = call.parameters["endringId"]
                val endringJson: String = call.receiveText()
                val updatedEndring = Json.decodeFromString<Fylkesdeling>(endringJson)

                val connection = DriverManager.getConnection(url, user, password)
                val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
                statement.setString(1, regId)

                val resultSet = statement.executeQuery()
                if (resultSet.next()) {
                    val reguleringJson = resultSet.getString("regulering")
                    val regulering = Json.decodeFromString<Regulering>(reguleringJson)

                    val endringIndex = regulering.endringer.indexOfFirst { it.id == endringId }
                    if (endringIndex != -1) {
                        val updatedEndringer = regulering.endringer.toMutableList()
                        updatedEndringer[endringIndex] = updatedEndring
                        val updatedRegulering = regulering.copy(endringer = updatedEndringer)

                        val updateStatement =
                            connection.prepareStatement("UPDATE regulering SET regulering = ?::jsonb WHERE ID = ?")
                        updateStatement.setString(1, Json.encodeToString(updatedRegulering))
                        updateStatement.setString(2, regId)
                        updateStatement.executeUpdate()

                        call.respond(HttpStatusCode.OK, "Endring updated successfully.")
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Endring not found.")
                    }
                } else {
                    call.respond(HttpStatusCode.NotFound, "Regulering not found.")
                }

                statement.close()
                connection.close()
            }
        }

        // Delete existing endring by id in regulering by id
        route("/reguleringer/{regId}/endringer/{endringId}") {
            delete {
                val regId = call.parameters["regId"]
                val endringId = call.parameters["endringId"]

                val connection = DriverManager.getConnection(url, user, password)
                val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
                statement.setString(1, regId)

                val resultSet = statement.executeQuery()
                if (resultSet.next()) {
                    val reguleringJson = resultSet.getString("regulering")
                    val regulering = Json.decodeFromString<Regulering>(reguleringJson)

                    val endringIndex = regulering.endringer.indexOfFirst { it.id == endringId }
                    if (endringIndex != -1) {
                        val updatedEndringer = regulering.endringer.toMutableList()
                        updatedEndringer.removeAt(endringIndex)
                        val updatedRegulering = regulering.copy(endringer = updatedEndringer)

                        val updateStatement =
                            connection.prepareStatement("UPDATE regulering SET regulering = ?::jsonb WHERE ID = ?")
                        updateStatement.setString(1, Json.encodeToString(updatedRegulering))
                        updateStatement.setString(2, regId)
                        updateStatement.executeUpdate()

                        call.respond(HttpStatusCode.OK, "Endring deleted successfully.")
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Endring not found.")
                    }
                } else {
                    call.respond(HttpStatusCode.NotFound, "Regulering not found.")
                }

                statement.close()
                connection.close()
            }
        }
    }
}
