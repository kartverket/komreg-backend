package no.kartverket.komreg.regulering.reform.format

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Fylkesnavn
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunenavn
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Koordinat
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.core.domain.Kretsnummer
import no.kartverket.komreg.core.domain.Kretstype
import no.kartverket.komreg.core.domain.Matrikkelnummer.Bruksnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer
import no.kartverket.komreg.core.domain.TeigId
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.regulering.reform.domain.type.NumberingScheme
import no.kartverket.komreg.transformation.Endring
import no.kartverket.komreg.transformation.FraEnTilMange
import no.kartverket.komreg.transformation.FraTil
import no.kartverket.komreg.transformation.Fylkeendring
import no.kartverket.komreg.transformation.Kommuneendring
import no.kartverket.komreg.transformation.Kretsendring
import no.kartverket.komreg.transformation.Matrikkelenhet
import no.kartverket.komreg.transformation.Matrikkelenhetendring
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.Teigendring
import no.kartverket.komreg.transformation.Vegadresseendring
import no.kartverket.komreg.transformation.Vegendring
import no.kartverket.komreg.transformation.div
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.toReformOps
import java.util.Base64
import kotlin.test.Test

/**
 * For hvert tilfelle i `ReformMappingTest` (lib/transformation) gir en spec samme mengde `ReformOp` som
 * `Reguleringsinput.toReformOps()`.
 */
class ResolveRoundTripTest {
    private val registry = runBlocking { SegmentRegistry.v1(NumberingScheme.Production) }
    private val dato = LocalDate(2024, 1, 1)
    private val vapen = "iVBORw0KGgo="

    private val fylker = listOf(30L, 31L, 32L, 33L).map { Fylke(Fylkesnummer(it), Fylkesnavn("Fylke $it"), null) }
    private val kommuner = listOf(12 to 35, 12 to 36, 12 to 50).map { (f, k) ->
        Kommune(
            kommunenummer = Kommunenummer(fnr(f.toLong()), klnr(k)),
            kommunenavn = Kommunenavn("Kommune $f$k"),
            gyldigTilDato = null,
            koordinatsystem = Koordinatsystem.UTM32,
            senterpunkt = Koordinat(123.0, 456.0),
            nedsattKonsesjonsgrense = false,
            godkjenteGardsnumre = "1-3",
            adresse = null,
            standardRekvirent = null,
            kommunevapen = Base64.getDecoder().decode(vapen),
        )
    }

    private fun fylkePayload(f: Int) = """{ "navn": "Fylke $f" }"""

    private fun kommunePayload(f: Int, k: Int) =
        """{ "navn": "Kommune $f$k", "koordinatsystem": "UTM32", "senterpunkt": { "x": 123.0, "y": 456.0 },
            "godkjenteGardsnumre": [ { "fra": 1, "til": 3 } ], "kommunevapen": "$vapen" }"""

    private fun fnr(n: Long) = Fylkesnummer(n)
    private fun klnr(n: Int) = Kommunenummer.Lopenummer(n.toByte())
    private fun serie(f: Long, k: Int, gnr: Int) = Matrikkelenhet.GardsnummerserieIdent(fnr(f), klnr(k), Gardsnummer(gnr))
    private fun grunneiendom(f: Long, k: Int, gnr: Int, bnr: Short) = serie(f, k, gnr) / Bruksnummer(bnr)

    private fun assertRoundTrip(operasjoner: String, vararg endringer: Endring) = runBlocking {
        val spec = Json.decodeFromString(
            ReformSpec.serializer(),
            """{ "navn": "R", "ikrafttredelse": "$dato", "operasjoner": [ $operasjoner ] }""",
        )
        val expected = Reguleringsinput("reg", dato, endringer.toList(), fylker, kommuner).toReformOps()

        val resolved = spec.resolve(registry).fold({ error("Uventede feil: $it") }, { it })

        assertThat(resolved.ops.toSet()).isEqualTo(expected.toSet())
        assertThat(resolved.ops.size).isEqualTo(expected.distinct().size)
        assertThat(ReformClaims.validate(resolved.ops).isRight()).isTrue()
    }

    @Test
    fun `fylke en til en`() = assertRoundTrip(
        """{ "op": "merge", "kind": "county", "from": ["30"], "into": { "new": ["31"], "payload": ${fylkePayload(31)} } }""",
        Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(31)))),
    )

    @Test
    fun fylkessammenslaaing() = assertRoundTrip(
        """{ "op": "merge", "kind": "county", "from": ["30", "31"], "into": { "new": ["33"], "payload": ${fylkePayload(33)} } }""",
        Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(33)))),
        Fylkeendring(FraEnTilMange(fnr(31), listOf(fnr(33))), sammenslaaing = true),
    )

    @Test
    fun fylkesdeling() = assertRoundTrip(
        """{ "op": "split", "kind": "county", "key": "30",
             "into": [ { "new": ["31"], "payload": ${fylkePayload(31)} }, { "new": ["32"], "payload": ${fylkePayload(32)} } ] }""",
        Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(31), fnr(32)))),
    )

    @Test
    fun `kommunesammenslaaing til ny kommune`() = assertRoundTrip(
        """{ "op": "merge", "kind": "municipality", "from": [["12", "34"], ["12", "40"]],
             "into": { "new": ["12", "50"], "payload": ${kommunePayload(12, 50)} } }""",
        Kommuneendring(FraEnTilMange(fnr(12), listOf(fnr(12))), FraEnTilMange(klnr(34), listOf(klnr(50)))),
        Kommuneendring(
            FraEnTilMange(fnr(12), listOf(fnr(12))),
            FraEnTilMange(klnr(40), listOf(klnr(50))),
            sammenslaaing = true,
        ),
    )

    @Test
    fun `kommunesammenslaaing til eksisterende kommune`() = assertRoundTrip(
        """{ "op": "merge", "kind": "municipality", "from": [["12", "34"], ["12", "40"]], "into": { "existing": ["12", "50"] } }""",
        Kommuneendring(
            FraEnTilMange(fnr(12), listOf(fnr(12))),
            FraEnTilMange(klnr(34), listOf(klnr(50))),
            sammenslaaing = true,
        ),
        Kommuneendring(
            FraEnTilMange(fnr(12), listOf(fnr(12))),
            FraEnTilMange(klnr(40), listOf(klnr(50))),
            sammenslaaing = true,
        ),
    )

    @Test
    fun kommunedeling() = assertRoundTrip(
        """{ "op": "split", "kind": "municipality", "key": ["12", "34"],
             "into": [ { "new": ["12", "35"], "role": "successor", "payload": ${kommunePayload(12, 35)} },
                       { "new": ["12", "36"], "payload": ${kommunePayload(12, 36)} } ] }""",
        Kommuneendring(FraEnTilMange(fnr(12), listOf(fnr(12))), FraEnTilMange(klnr(34), listOf(klnr(35), klnr(36)))),
    )

    @Test
    fun `renummerering av kommune`() = assertRoundTrip(
        """{ "op": "merge", "kind": "municipality", "from": [["12", "34"]],
             "into": { "new": ["12", "50"], "payload": ${kommunePayload(12, 50)} } }""",
        Kommuneendring(FraEnTilMange(fnr(12), listOf(fnr(12))), FraEnTilMange(klnr(34), listOf(klnr(50)))),
    )

    @Test
    fun vegflytting() = assertRoundTrip(
        """{ "op": "move", "kind": "addressCode", "key": ["12", "34", 100], "to": ["12", "35", 200] }""",
        Vegendring(
            FraEnTilMange(fnr(12), listOf(fnr(12))),
            FraEnTilMange(klnr(34), listOf(klnr(35))),
            FraEnTilMange(Adressekode(100), listOf(Adressekode(200))),
        ),
    )

    @Test
    fun vegdeling() = assertRoundTrip(
        """{ "op": "split", "kind": "addressCode", "key": ["12", "34", 100],
             "into": [ { "new": ["12", "35", 200], "role": "identity" }, { "new": ["12", "36", 300] } ] }""",
        Vegendring(
            FraEnTilMange(fnr(12), listOf(fnr(12))),
            FraEnTilMange(klnr(34), listOf(klnr(35), klnr(36))),
            FraEnTilMange(Adressekode(100), listOf(Adressekode(200), Adressekode(300))),
        ),
    )

    @Test
    fun `vegadresse og krets`() = assertRoundTrip(
        """{ "op": "move", "kind": "addressNumber", "key": ["12", "34", 100, 1], "to": ["12", "35", 200, 2] },
           { "op": "move", "kind": "districtNumber", "key": ["12", "34", "valg", 1], "to": ["12", "35", "valg", 2] }""",
        Vegadresseendring(
            FraTil(fnr(12), fnr(12)),
            FraTil(klnr(34), klnr(35)),
            FraTil(Adressekode(100), Adressekode(200)),
            FraTil(Adressenummernummer(1), Adressenummernummer(2)),
        ),
        Kretsendring(
            FraTil(fnr(12), fnr(12)),
            FraTil(klnr(34), klnr(35)),
            FraTil(Kretsnummer(1), Kretsnummer(2)),
            FraTil(Kretstype("valg"), Kretstype("valg")),
        ),
    )

    @Test
    fun `matrikkelenhet med bruksnumre`() = assertRoundTrip(
        """{ "op": "move", "kind": "landNumber", "key": ["12", "34", 40], "to": ["12", "35", 60],
             "then": [ { "op": "move", "kind": "titleNumber", "key": 1, "to": ["12", "35", 70, 1] } ] }""",
        Matrikkelenhetendring(
            FraTil(fnr(12), fnr(12)),
            FraTil(klnr(34), klnr(35)),
            Gardsnummer(40),
            Gardsnummer(60),
            mapOf(Bruksnummer(1) to grunneiendom(12, 35, 70, 1)),
        ),
    )

    @Test
    fun `matrikkelenhet uten serieendring`() = assertRoundTrip(
        """{ "op": "move", "kind": "titleNumber", "key": ["12", "34", 40, 1], "to": ["12", "34", 40, 9] }""",
        Matrikkelenhetendring(
            FraTil(fnr(12), fnr(12)),
            FraTil(klnr(34), klnr(34)),
            Gardsnummer(40),
            Gardsnummer(40),
            mapOf(Bruksnummer(1) to grunneiendom(12, 34, 40, 9)),
        ),
    )

    @Test
    fun `serie som utgaar`() = assertRoundTrip(
        """{ "op": "split", "kind": "landNumber", "key": ["12", "34", 40], "into": [],
             "then": [ { "op": "move", "kind": "titleNumber", "key": 1, "to": ["12", "35", 60, 1] },
                       { "op": "move", "kind": "titleNumber", "key": 2, "to": ["12", "35", 70, 2] } ] }""",
        Matrikkelenhetendring(
            FraTil(fnr(12), fnr(12)),
            FraTil(klnr(34), klnr(35)),
            Gardsnummer(40),
            null,
            mapOf(
                Bruksnummer(1) to grunneiendom(12, 35, 60, 1),
                Bruksnummer(2) to grunneiendom(12, 35, 70, 2),
            ),
        ),
    )

    @Test
    fun teig() = assertRoundTrip(
        """{ "op": "move", "kind": "parcel", "key": ["12", "34", 1], "to": ["12", "35", 2] }""",
        Teigendring(FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)), FraTil(TeigId(1), TeigId(2))),
    )
}
