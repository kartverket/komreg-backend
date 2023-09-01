package no.kartverket.komreg

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.transformation.*
import java.util.Base64
import javax.sql.DataSource

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

fun Application.routes(metricsRegistry: PrometheusMeterRegistry, dataSource: DataSource) {
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.invalidateCaches()
            ConfigFactory.load("properties.conf")
        }
    }
    val kommuneService = KommuneServiceManager(bootContext).kommuneService

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

        route("/kommuner/{kommuneId}/fordelingsparametre") {
            get {
                val kommuneId = call.parameters["kommuneId"]

                // TODO: PoC for uthenting av fordelingsparametre for kommune
                val gårdsnumre = mutableListOf<String>()
                val adresseparseller = mutableListOf<Adresseparsell>()
                val kretser = mutableListOf<Krets>()
                val teiger = mutableListOf<Teig>()

                dataSource.connection.use { connection ->
                    val gårdsnummerStatement =
                        connection.prepareStatement("SELECT DISTINCT gardsnr FROM matrikkelenhet WHERE kommuneid = ?")
                    gårdsnummerStatement.setString(1, kommuneId)
                    val gårdsnummerResultSet = gårdsnummerStatement.executeQuery()
                    while (gårdsnummerResultSet.next()) {
                        gårdsnumre.add(gårdsnummerResultSet.getString("gardsnr"))
                    }

                    val adresseparsellStatement =
                        connection.prepareStatement("SELECT adressekode, adressenavn FROM veg WHERE kommuneid = ?")
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
                        connection.prepareStatement("SELECT kretsnavn, kretsnr, class FROM krets k LEFT JOIN kommunerforkrets kfk ON k.id = kfk.kretsid WHERE kfk.kommuneid = ?")
                    kretsStatement.setString(1, kommuneId)
                    val kretsResultSet = kretsStatement.executeQuery()
                    while (kretsResultSet.next()) {
                        kretser.add(
                            Krets(
                                kretsnummer = kretsResultSet.getString("kretsnr"),
                                kretsnavn = kretsResultSet.getString("kretsnavn"),
                                type = kretsResultSet.getString("class"),
                            ),
                        )
                    }

                    val teigStatement =
                        connection.prepareStatement(
                            "SELECT t.id AS id, koordinatsystemkodeid, nord, ost FROM teig t LEFT JOIN teigformatrikkelenhet tfm ON t.id = tfm.teigid\n" +
                                "    WHERE matrikkelenhetid IN (SELECT id FROM matrikkelenhet WHERE kommuneid = ? AND gardsnr = 0)",
                        )
                    teigStatement.setString(1, kommuneId)
                    val teigResultSet = teigStatement.executeQuery()
                    while (teigResultSet.next()) {
                        teiger.add(
                            Teig(
                                id = teigResultSet.getString("id"),
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
data class Adresseparsell(
    val adressekode: String,
    val adressenavn: String,
)

@Serializable
data class Krets(
    val kretsnummer: String,
    val kretsnavn: String,
    val type: String,
)

@Serializable
data class Teig(
    val id: String,
    val koordinatsystemkodeid: Int,
    val nord: Double,
    val øst: Double,
)

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
