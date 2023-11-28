package no.kartverket.komreg.routes

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.verdi
import no.kartverket.komreg.integration.KommuneServiceManager
import java.util.Base64
import javax.sql.DataSource

fun Application.grunndataRoutes(dataSource: DataSource) {
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.invalidateCaches()
            ConfigFactory.load("properties.conf")
        }
    }
    val kommuneService = KommuneServiceManager(bootContext).kommuneService

    routing {
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
                                fylkesnummer = kommuneFraMatrikkel.kommunenummer.fylkesnummer.verdi(),
                                kommunenummer = kommuneFraMatrikkel.kommunenummer.lopenummer.verdi(),
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

        route("/kommuner/{kommuneId}/fordelingsparametre") {
            get {
                val kommuneId = call.parameters["kommuneId"]
                call.application.log.info("Henter fordelingsparametre for kommune $kommuneId")

                // TODO: PoC for uthenting av fordelingsparametre for kommune
                val gårdsnumre = mutableListOf<String>()
                val adresseparseller = mutableListOf<Adresseparsell>()
                val kretser = mutableListOf<Krets>()
                val teiger = mutableListOf<Teig>()

                dataSource.connection.use { connection ->
                    val gårdsnummerStatement =
                        connection.prepareStatement("SELECT DISTINCT gardsnr FROM matrikkelenhet WHERE kommuneid = ? AND gardsnr != 0 ORDER BY gardsnr")
                    gårdsnummerStatement.setString(1, kommuneId)
                    val gårdsnummerResultSet = gårdsnummerStatement.executeQuery()
                    while (gårdsnummerResultSet.next()) {
                        gårdsnumre.add(gårdsnummerResultSet.getString("gardsnr"))
                    }

                    val adresseparsellStatement =
                        connection.prepareStatement("SELECT adressekode, adressenavn FROM veg WHERE kommuneid = ? ORDER BY adressekode")
                    adresseparsellStatement.setString(1, kommuneId)
                    val adresseparsellResultSet = adresseparsellStatement.executeQuery()
                    while (adresseparsellResultSet.next()) {
                        adresseparseller.add(
                            Adresseparsell(
                                adressekode = adresseparsellResultSet.getString("adressekode"),
                                adressenavn = adresseparsellResultSet.getString("adressenavn"),
                            ),
                        )
                    }

                    val kretsStatement =
                        connection.prepareStatement(
                            "SELECT kr.kretsnavn, ko.kodeverdi, " +
                                "nvl(kr.bispedomme, 0) * 1000000 + nvl(kr.prosti, 0) * 10000 + nvl(kr.prestegjeld, 0) * 100 + kr.kretsnr AS kretsnummer " +
                                "FROM krets kr JOIN kommunerforkrets kfk ON kfk.kretsid = kr.id JOIN kode ko ON ko.id = kr.kretstypekodeid WHERE kfk.kommuneid = ? " +
                                "ORDER BY ko.kodeverdi, kretsnummer",
                        )
                    kretsStatement.setString(1, kommuneId)
                    val kretsResultSet = kretsStatement.executeQuery()
                    while (kretsResultSet.next()) {
                        kretser.add(
                            Krets(
                                kretsnummer = kretsResultSet.getString("kretsnummer"),
                                kretstype = kretsResultSet.getString("kodeverdi"),
                                kretsnavn = kretsResultSet.getString("kretsnavn"),
                            ),
                        )
                    }

                    val teigStatement =
                        connection.prepareStatement(
                            "SELECT t.id AS id, koordinatsystemkodeid, nord, ost FROM teig t LEFT JOIN teigformatrikkelenhet tfm ON t.id = tfm.teigid " +
                                "WHERE matrikkelenhetid IN (SELECT id FROM matrikkelenhet WHERE kommuneid = ? AND gardsnr = 0) " +
                                "ORDER BY id",
                        )
                    teigStatement.setString(1, kommuneId)
                    val teigResultSet = teigStatement.executeQuery()
                    while (teigResultSet.next()) {
                        teiger.add(
                            Teig(
                                teigId = teigResultSet.getString("id"),
                                koordinatsystemkodeid = teigResultSet.getInt("koordinatsystemkodeid"),
                                nord = teigResultSet.getDouble("nord"),
                                øst = teigResultSet.getDouble("ost"),
                            ),
                        )
                    }
                }

                call.respond(
                    Fordelingsparametre(
                        gårdsnumre = gårdsnumre,
                        adresseparseller = adresseparseller,
                        kretser = kretser,
                        teiger = teiger,
                    ),
                )
            }
        }

        route("/kommuner/{kommuneId}/fordelingsparametre/veg/{adressekode}") {
            get {
                val kommuneId = call.parameters["kommuneId"]
                val adressekode = call.parameters["adressekode"]
                call.application.log.info("Henter fordelingsparametre for veg $adressekode i kommune $kommuneId")

                // TODO: PoC for uthenting av fordelingsparametre for veg i kommune
                val vegadresser = mutableListOf<Vegadresse>()

                dataSource.connection.use { connection ->
                    val vegadresseStatement =
                        connection.prepareStatement(
                            "SELECT v.adressekode, v.adressenavn, a.nr, a.bokstav FROM adresse a LEFT JOIN veg v ON a.vegid = v.id " +
                                "WHERE v.kommuneid = ? AND v.adressekode = ? ORDER BY a.nr, a.bokstav",
                        )
                    vegadresseStatement.setString(1, kommuneId)
                    vegadresseStatement.setString(2, adressekode)
                    val vegadresseResultSet = vegadresseStatement.executeQuery()
                    while (vegadresseResultSet.next()) {
                        vegadresser.add(
                            Vegadresse(
                                adressekode = vegadresseResultSet.getString("adressekode"),
                                adressenavn = vegadresseResultSet.getString("adressenavn"),
                                nr = vegadresseResultSet.getString("nr"),
                                bokstav = vegadresseResultSet.getString("bokstav"),
                            ),
                        )
                    }
                }

                call.respond(
                    FordelingsparametreVeg(
                        vegadresser = vegadresser,
                    ),
                )
            }
        }
    }
}

@Serializable
data class Fordelingsparametre(
    val gårdsnumre: List<String>,
    val adresseparseller: List<Adresseparsell>,
    val kretser: List<Krets>,
    val teiger: List<Teig>,
)

@Serializable
data class FordelingsparametreVeg(
    val vegadresser: List<Vegadresse>,
)

@Serializable
data class Adresseparsell(
    val adressekode: String,
    val adressenavn: String,
)

@Serializable
data class Krets(
    val kretsnummer: String,
    val kretstype: String,
    val kretsnavn: String,
)

@Serializable
data class Teig(
    val teigId: String,
    val koordinatsystemkodeid: Int,
    val nord: Double,
    val øst: Double,
)

@Serializable
data class Vegadresse(
    val adressekode: String,
    val adressenavn: String,
    val nr: String,
    val bokstav: String?,
)
