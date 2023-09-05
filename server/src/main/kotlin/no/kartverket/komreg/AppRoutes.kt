package no.kartverket.komreg

import com.typesafe.config.ConfigFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.transformation.*
import java.sql.Date
import java.sql.DriverManager
import java.util.Base64

@Serializable
data class Regulering(
    val id: String,
    val navn: String,
    val dato: LocalDate,
    val endringer: List<Fylkesdeling>,
) {
    fun toReguleringsinput(): Reguleringsinput {
        return Reguleringsinput(
            id,
            dato,
            endringer = endringer.flatMap { fylkesdeling ->
                fylkesdeling.nyeFylker.flatMap { fylke ->
                    fylke.kommuner.map { kommune ->
                        Kommuneendring(
                            Kommunenummer(kommune.kommunenummer.toLong()),
                            Kommunenummer(kommune.nyttKommunenummer.toLong()),
                        )
                    }
                }
            },
            fylker = endringer.flatMap { fylkesdeling ->
                fylkesdeling.nyeFylker.filter { it.skalOpprettes == true }.map {
                    Fylke(
                        Fylkesnummer(it.fylkesnummer.toLong()),
                        Fylkesnavn(it.navn),
                        null, // TODO: Bør bruke en annen type
                    )
                }
            },
            kommuner = endringer.flatMap { fylkesdeling ->
                fylkesdeling.nyeFylker.flatMap { fylke ->
                    fylke.kommuner.filter { nyKommune -> nyKommune.skalOpprettes == true }.map { nyKommune ->
                        Kommune(
                            kommunenummer = Kommunenummer(nyKommune.nyttKommunenummer.toLong()),
                            kommunenavn = Kommunenavn(nyKommune.navn),
                            gyldigTilDato = null,
                            koordinatsystem = nyKommune.koordinatsystem,
                            senterpunkt = Koordinat(
                                x = nyKommune.senterpunkt.x,
                                y = nyKommune.senterpunkt.y,
                            ),
                            nedsattKonsesjonsgrense = nyKommune.nedsattKonsesjonsgrense,
                            godkjenteGardsnumre = nyKommune.godkjenteGardsnumre.joinToString(",") { serie -> serie.join() },
                            adresse = nyKommune.adresse?.let {
                                Postadresse(
                                    adresselinje1 = it.adresselinje1,
                                    adresselinje2 = it.adresselinje2,
                                    postnummer = it.postnummer,
                                    poststed = it.poststed,
                                )
                            },
                            standardRekvirent = nyKommune.standardRekvirent?.let {
                                StandardRekvirent(it.orgnummer, it.navn)
                            },
                            // TODO: Vil feile dersom kommunevåpen ikke er satt
                            kommunevapen = Base64.getDecoder().decode(nyKommune.kommunevapen),
                        )
                    }
                }
            },
        )
    }
}

@Serializable
data class Fylkesdeling(
    val id: String,
    val navn: String,
    val type: String,
    val gammeltFylke: FylkeDTO,
    val nyeFylker: List<NyttFylke>,
)

@Serializable
data class FylkeDTO(
    val navn: String,
    val fylkesnummer: String,
)

@Serializable
data class NyttFylke(
    val navn: String,
    val fylkesnummer: String,
    val kommuner: List<NyKommune>,
    val skalOpprettes: Boolean? = false,
)

@Serializable
data class KommuneDTO(
    val navn: String,
    val kommunenummer: String,
    val gyldigTilDato: LocalDate?,
    val koordinatsystem: Koordinatsystem,
    val senterpunkt: KoordinatDTO,
    val nedsattKonsesjonsgrense: Boolean,
    val godkjenteGardsnumre: List<Gardsnummerserie>,
    val adresse: AdresseDTO?,
    val standardRekvirent: StandardRekvirentDTO?,
    val kommunevapen: String?,
)

@Serializable
data class NyKommune(
    val navn: String,
    val kommunenummer: String,
    val gyldigTilDato: LocalDate?,
    val koordinatsystem: Koordinatsystem,
    val senterpunkt: KoordinatDTO,
    val nedsattKonsesjonsgrense: Boolean,
    val godkjenteGardsnumre: List<Gardsnummerserie>,
    val adresse: AdresseDTO?,
    val standardRekvirent: StandardRekvirentDTO?,
    val kommunevapen: String?,
    val nyttKommunenummer: String,
    val skalOpprettes: Boolean? = false,
)

@Serializable
data class KoordinatDTO(
    val x: Double,
    val y: Double,
)

@Serializable
data class Gardsnummerserie(
    val fra: Int,
    val til: Int,
)

@Serializable
data class AdresseDTO(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val postnummer: String,
    val poststed: String,
)

@Serializable
data class StandardRekvirentDTO(
    val orgnummer: String,
    val navn: String,
)

fun Application.routes(metricsRegistry: PrometheusMeterRegistry) {
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.invalidateCaches()
            ConfigFactory.load("properties.conf")
        }
    }
    val kommuneService = KommuneServiceManager(bootContext).kommuneService

    val url = env["DB_KOMREG_JDBC_URL"]
    val user = env["DB_KOMREG_USERNAME"]
    val password = env["DB_KOMREG_PASSWORD"]

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

        route("/fylker") {
            get {
                call.application.log.info("Fylker endpoint called")
                val fylkerFraMatrikkel = kommuneService.findAlleFylker()
                val fylker = mutableListOf<FylkeDTO>()
                fylkerFraMatrikkel
                    .filter { it.gyldigTilDato == null }
                    .forEach { fylkeFraMatrikkel ->
                        fylker.add(
                            FylkeDTO(
                                navn = fylkeFraMatrikkel.fylkesnavn.name,
                                fylkesnummer = fylkeFraMatrikkel.fylkesnummer.verdi(),
                            ),
                        )
                    }
                call.respond(fylker)
            }
        }

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
                    logger.info("No reguleringer found.")
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

        route("/kommuner") {
            get {
                call.application.log.info("Kommuner endpoint called")
                val kommunerFraMatrikkel = kommuneService.findAlleKommuner()
                val kommuner = mutableListOf<KommuneDTO>()
                kommunerFraMatrikkel
                    .filter { it.gyldigTilDato == null }
                    .forEach { kommuneFraMatrikkel ->
                        kommuner.add(
                            KommuneDTO(
                                navn = kommuneFraMatrikkel.kommunenavn.name,
                                kommunenummer = kommuneFraMatrikkel.kommunenummer.verdi(),
                                gyldigTilDato = kommuneFraMatrikkel.gyldigTilDato,
                                koordinatsystem = kommuneFraMatrikkel.koordinatsystem,
                                senterpunkt = KoordinatDTO(
                                    x = kommuneFraMatrikkel.senterpunkt.x,
                                    y = kommuneFraMatrikkel.senterpunkt.y,
                                ),
                                nedsattKonsesjonsgrense = kommuneFraMatrikkel.nedsattKonsesjonsgrense,
                                godkjenteGardsnumre = godkjenteGardsnumreTilListe(kommuneFraMatrikkel.godkjenteGardsnumre),
                                adresse = kommuneFraMatrikkel.adresse?.let {
                                    AdresseDTO(
                                        adresselinje1 = it.adresselinje1,
                                        adresselinje2 = it.adresselinje2,
                                        postnummer = it.postnummer,
                                        poststed = it.poststed,
                                    )
                                },
                                standardRekvirent = kommuneFraMatrikkel.standardRekvirent?.let {
                                    StandardRekvirentDTO(it.orgnummer, it.navn)
                                },
                                kommunevapen = kommuneFraMatrikkel.kommunevapen?.let {
                                    Base64.getEncoder().encodeToString(it)
                                },
                            ),
                        )
                    }
                call.respond(kommuner)
            }
        }
    }
}

fun godkjenteGardsnumreTilListe(godkjenteGardsnumre: String?): List<Gardsnummerserie> {
    if (godkjenteGardsnumre == null) return emptyList()

    val serier = godkjenteGardsnumre.split(',')
    if (serier.isEmpty()) return emptyList()

    val liste = mutableListOf<Gardsnummerserie>()
    for (serie in serier) {
        if (serie.isNotBlank()) {
            if (serie.contains('-')) {
                val (fra, til) = serie.split('-')
                liste.add(Gardsnummerserie(fra.toInt(), til.toInt()))
            } else {
                liste.add(Gardsnummerserie(serie.toInt(), serie.toInt()))
            }
        }
    }

    return liste
}

fun Gardsnummerserie.join(): String = if (this.fra == this.til) "${this.fra}" else "${this.fra}-${this.til}"
