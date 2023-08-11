package no.kartverket.komreg

import com.typesafe.config.ConfigFactory
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.transformation.*
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.LocalDate
import java.util.Date
import no.kartverket.komreg.core.domain.Fylke as NyttFylke
import no.kartverket.komreg.core.domain.Kommune as NyKommune



@Serializable
data class Regulering(
    val id: String,
    val navn: String,
    val dato: String, // Date?
    val endringer: List<Fylkesdeling>, // Endringer som sealed classes og diskriminator?
) {
    fun toReguleringsinput(): Reguleringsinput {
        return Reguleringsinput(
            id,
            LocalDate.parse(dato),
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
                    NyttFylke(
                        Fylkesnummer(it.fylkesnummer.toLong()),
                        Fylkesnavn(it.navn),
                        null, // TODO: Bør bruke en annen type
                    )
                }
            },
            kommuner = endringer.flatMap { fylkesdeling ->
                fylkesdeling.nyeFylker.flatMap { fylke ->
                    fylke.kommuner.filter { it.skalOpprettes == true }.map {
                        NyKommune(
                            Kommunenummer(it.nyttKommunenummer.toLong()),
                            Kommunenavn(it.navn),
                            null, // TODO: Bør bruke en annen type
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
    val gammeltFylke: Fylke,
    val nyeFylker: List<Fylke>,
)

@Serializable
data class Fylke(
    val navn: String,
    val fylkesnummer: String,
    val kommuner: List<Kommune>,
    val skalOpprettes: Boolean? = false,
)

@Serializable
data class Senterpunkt(
    val x: Double,
    val y: Double,
)

@Serializable
data class Gardsnummerserie(
    val fra: Int,
    val til: Int,
)

@Serializable
data class Kommune(
    val navn: String,
    val kommunenummer: String,
    val nyttKommunenummer: String,
    val koordinatsystem: String? = null,
    val senterpunkt: Senterpunkt? = null,
    val nedsattKonsesjonsgrense: Boolean? = null,
    val brukteGardsnummer: List<Gardsnummerserie>? = null,
    val adresselinje1: String? = null,
    val adresselinje2: String? = null,
    val postnummer: String? = null,
    val poststed: String? = null,
    val standardRekvirentorgnummer: String? = null,
    val standardRekvirentnavn: String? = null,
    val kommunevapen: String? = null,
    val skalOpprettes: Boolean? = false,
)

@Serializable
data class KommuneDTO(
    val kommunenavn: String,
    val kommunenr: String,
)

@Serializable
data class FylkeDTO(
    val fylkesnavn: String,
    val fylkesnr: String,
)

fun Application.routes() {
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.invalidateCaches()
            ConfigFactory.load("properties.conf")
        }
    }
    val kommuneService = KommuneServiceManager(bootContext).kommuneService

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
        route("/actuator/health") {
            get {
                call.respond("OK")
            }
        }
        route("/transform/status") {
            get {
                call.respond(transformStatuses)
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
                                kommunenavn = kommuneFraMatrikkel.kommunenavn.name,
                                kommunenr = kommuneFraMatrikkel.kommunenummer.verdi(),
                            ),
                        )
                    }
                call.respond(kommuner)
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
                                fylkesnavn = fylkeFraMatrikkel.fylkesnavn.name,
                                fylkesnr = fylkeFraMatrikkel.fylkesnummer.verdi(),
                            ),
                        )
                    }
                call.respond(fylker)
            }
        }
        route("/testdb") {
            post {
                connectToGCP()
                call.respond("OK")
            }
        }
        route("/writetransform") {
            post {
                insertIntoTransformation("dev")
                call.respond("OK")
            }
        }
    }
}

fun insertIntoTransformation(name: String) {
    val jdbcUrl = env["DB_TRANSFORMATION_JDBC_URL"]
    val user = env["DB_TRANSFORMATION_USERNAME"]
    val password = env["DB_TRANSFORMATION_PASSWORD"]

    DriverManager.getConnection(jdbcUrl, user, password).use { connection ->
        val sql = """
            INSERT INTO public.transformation (name, created_at, updated_at) 
            VALUES (?, ?, ?)
        """
        connection.prepareStatement(sql).use { preparedStatement ->
            preparedStatement.setString(1, name)
            preparedStatement.setTimestamp(2, Timestamp(Date().time))
            preparedStatement.setTimestamp(3, Timestamp(Date().time))
            preparedStatement.executeUpdate()
        }
    }
}

fun connectToGCP() {
    println("Connecting to PostgreSQL database...")
    val url = "jdbc:postgresql://34.116.175.97:5432/postgres"
    val user = "komreg-db-dev-user"
    val password = "password"

    // Connect to the database
    DriverManager.getConnection(url, user, password).use { connection ->
        println("Connected to the PostgreSQL server successfully.")

        // Insert sample data
        insertSampleData(connection)

        // Retrieve all entries from new_table
        retrieveAllEntries(connection)
    }
}

fun insertSampleData(connection: Connection) {
    val sql = "INSERT INTO new_table (id, name, valid) VALUES (?, ?, ?)"

    connection.prepareStatement(sql).use { preparedStatement ->
        preparedStatement.setInt(1, 1)
        preparedStatement.setString(2, "Testname")
        preparedStatement.setBoolean(3, true)

        val rowAffected = preparedStatement.executeUpdate()
        println("Rows affected: $rowAffected")
    }
}

fun retrieveAllEntries(connection: Connection): List<Map<String, Any?>> {
    val sql = "SELECT * FROM new_table"
    val results = mutableListOf<Map<String, Any?>>()

    connection.prepareStatement(sql).use { preparedStatement ->
        val resultSet = preparedStatement.executeQuery()

        val metaData = resultSet.metaData
        val columnCount = metaData.columnCount

        while (resultSet.next()) {
            val row = mutableMapOf<String, Any?>()
            for (i in 1..columnCount) {
                row[metaData.getColumnName(i)] = resultSet.getObject(i)
            }
            results.add(row)
        }
    }

    // For demonstration purposes, printing the results
    println("Retrieved entries: $results")

    return results
}
