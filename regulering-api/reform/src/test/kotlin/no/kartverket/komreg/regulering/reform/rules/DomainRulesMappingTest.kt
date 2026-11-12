package no.kartverket.komreg.regulering.reform.rules

import assertk.assertThat
import assertk.assertions.isEmpty
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
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
import no.kartverket.komreg.regulering.reform.format.SegmentRegistry
import no.kartverket.komreg.regulering.reform.format.v1
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
import kotlin.test.Test

/** Dagens gyldige DTO-er (tilfellene i `ReformMappingTest`) skal bestå domenereglene. */
class DomainRulesMappingTest {
    private val registry = runBlocking { SegmentRegistry.v1(NumberingScheme.Production) }
    private val dato = LocalDate(2024, 1, 1)
    private val fylker = listOf(30L, 31L, 32L, 33L).map { Fylke(Fylkesnummer(it), Fylkesnavn("Fylke $it"), null) }
    private val kommuner = listOf(12 to 35, 12 to 36, 12 to 50, 31 to 1, 33 to 5).map { (f, k) ->
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
            kommunevapen = byteArrayOf(1, 2, 3),
        )
    }

    private fun fnr(n: Long) = Fylkesnummer(n)
    private fun klnr(n: Int) = Kommunenummer.Lopenummer(n.toByte())
    private fun serie(f: Long, k: Int, gnr: Int) = Matrikkelenhet.GardsnummerserieIdent(fnr(f), klnr(k), Gardsnummer(gnr))
    private fun grunneiendom(f: Long, k: Int, gnr: Int, bnr: Short) = serie(f, k, gnr) / Bruksnummer(bnr)

    private fun assertPasses(vararg endringer: Endring) = runBlocking {
        val ops = Reguleringsinput("reg", dato, endringer.toList(), fylker, kommuner).toReformOps()
        val claims = ReformClaims.validate(ops).fold({ error("Ugyldige claims: $it") }, { it })
        assertThat(claims.validateDomain(registry).leftOrNull()?.toList().orEmpty()).isEmpty()
    }

    @Test
    fun fylker() {
        assertPasses(Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(31)))))
        assertPasses(
            Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(33)))),
            Fylkeendring(FraEnTilMange(fnr(31), listOf(fnr(33))), sammenslaaing = true),
        )
        assertPasses(Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(31), fnr(32)))))
    }

    @Test
    fun kommuner() {
        val fylke = FraEnTilMange(fnr(12), listOf(fnr(12)))
        assertPasses(Kommuneendring(fylke, FraEnTilMange(klnr(34), listOf(klnr(50)))))
        assertPasses(Kommuneendring(fylke, FraEnTilMange(klnr(34), listOf(klnr(35), klnr(36)))))
        assertPasses(
            Kommuneendring(fylke, FraEnTilMange(klnr(34), listOf(klnr(50)))),
            Kommuneendring(fylke, FraEnTilMange(klnr(40), listOf(klnr(50))), sammenslaaing = true),
        )
    }

    @Test
    fun `veg, vegadresse og krets`() {
        assertPasses(
            Vegendring(
                FraEnTilMange(fnr(12), listOf(fnr(12))),
                FraEnTilMange(klnr(34), listOf(klnr(35), klnr(36))),
                FraEnTilMange(Adressekode(100), listOf(Adressekode(200), Adressekode(300))),
            ),
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
    }

    @Test
    fun `matrikkelenheter og teig`() {
        assertPasses(
            Matrikkelenhetendring(
                FraTil(fnr(12), fnr(12)),
                FraTil(klnr(34), klnr(35)),
                Gardsnummer(40),
                Gardsnummer(60),
                mapOf(Bruksnummer(1) to grunneiendom(12, 35, 70, 1)),
            ),
            Matrikkelenhetendring(
                FraTil(fnr(12), fnr(12)),
                FraTil(klnr(34), klnr(35)),
                Gardsnummer(41),
                null,
                mapOf(Bruksnummer(1) to grunneiendom(12, 35, 61, 1)),
            ),
            Teigendring(FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)), FraTil(TeigId(1), TeigId(2))),
        )
    }

    @Test
    fun `typisk fylkesdeling med kommuneflytting`() {
        assertPasses(
            Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(31), fnr(33)))),
            Kommuneendring(FraEnTilMange(fnr(30), listOf(fnr(31))), FraEnTilMange(klnr(1), listOf(klnr(1)))),
            Kommuneendring(FraEnTilMange(fnr(30), listOf(fnr(33))), FraEnTilMange(klnr(7), listOf(klnr(5)))),
        )
    }
}
