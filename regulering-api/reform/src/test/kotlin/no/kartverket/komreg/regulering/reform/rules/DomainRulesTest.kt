package no.kartverket.komreg.regulering.reform.rules

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Koordinat
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.core.domain.Kretstype
import no.kartverket.komreg.core.domain.Matrikkelnummer.Bruksnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Festenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Seksjonsnummer
import no.kartverket.komreg.core.domain.TeigId
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.identTypeOf1
import no.kartverket.komreg.integration.spi.identTypeOf2
import no.kartverket.komreg.integration.spi.identTypeOf3
import no.kartverket.komreg.integration.spi.identTypeOf4
import no.kartverket.komreg.integration.spi.identTypeOf7
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.regulering.reform.domain.type.NumberingScheme
import no.kartverket.komreg.regulering.reform.format.SegmentRegistry
import no.kartverket.komreg.regulering.reform.format.v1
import no.kartverket.komreg.transformation.Matrikkelenhet
import no.kartverket.komreg.transformation.div
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.ReformOp
import kotlin.test.Test

class DomainRulesTest {
    private val registry = runBlocking { SegmentRegistry.v1(NumberingScheme.Production) }

    private val fylkeType = runBlocking { identTypeOf1<Fylkesnummer>() }
    private val kommuneType = runBlocking { identTypeOf2<Fylkesnummer, Kommunenummer.Lopenummer>() }
    private val vegType = runBlocking { identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode>() }
    private val vegadresseType =
        runBlocking { identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode, Adressenummernummer>() }
    private val kretstypeType = runBlocking { identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Kretstype>() }
    private val teigType = runBlocking {
        identTypeOf7<Fylkesnummer, Kommunenummer.Lopenummer, Gardsnummer, Bruksnummer, Festenummer, Seksjonsnummer, TeigId>()
    }

    private val fylkesdata = Fylkesdata("Fylke")
    private val vapen = byteArrayOf(1, 2, 3)

    private fun kommunedata(vapen: ByteArray? = this.vapen) = Kommunedata(
        navn = "KOMMUNE",
        koordinatsystem = Koordinatsystem.UTM32,
        senterpunkt = Koordinat(1.0, 2.0),
        nedsattKonsesjonsgrense = false,
        godkjenteGardsnumre = "1-3",
        adresse = null,
        standardRekvirentOrgnummer = null,
        ikrafttredelsesdato = LocalDate(2027, 1, 1),
        kommunevapen = vapen,
    )

    private fun fylke(f: Long) = fylkeType(Fylkesnummer(f))
    private fun kommune(f: Long, k: Int) = kommuneType(Fylkesnummer(f), Kommunenummer.Lopenummer(k.toByte()))
    private fun veg(f: Long, k: Int, kode: Int) =
        vegType(Fylkesnummer(f), Kommunenummer.Lopenummer(k.toByte()), Adressekode(kode))
    private fun vegadresse(kode: Int, nr: Short) = vegadresseType(
        Fylkesnummer(12), Kommunenummer.Lopenummer(34), Adressekode(kode), Adressenummernummer(nr),
    )
    private fun serie(k: Int, gnr: Int) =
        Matrikkelenhet.GardsnummerserieIdent(Fylkesnummer(12), Kommunenummer.Lopenummer(k.toByte()), Gardsnummer(gnr))
    private fun grunneiendom(k: Int, gnr: Int, bnr: Short) = serie(k, gnr) / Bruksnummer(bnr)
    private fun teig(k: Int, bnr: Short, id: Long, gnr: Int = 0) = teigType(
        Fylkesnummer(12), Kommunenummer.Lopenummer(k.toByte()), Gardsnummer(gnr), Bruksnummer(bnr),
        Festenummer(0), Seksjonsnummer(0), TeigId(id),
    )

    private fun errors(vararg ops: ReformOp): List<DomainErr> {
        val claims = ReformClaims.validate(ops.toList()).fold({ error("Ugyldige claims: $it") }, { it })
        return claims.validateDomain(registry).leftOrNull()?.toList().orEmpty()
    }

    private fun assertRule(errors: List<DomainErr>, rule: String) {
        assertThat(errors.map { it.rule }).containsExactly(rule)
    }

    @Test
    fun `fylke kan ikke flyttes, og hintet foreslår renummerering`() {
        val errors = errors(ReformOp.Move(fylke(30), fylke(31)))
        assertRule(errors, "ActionNotSupported")
        assertThat(errors.single().hint).isEqualTo(
            "Fylke kan ikke flyttes. Bruk `merge` med `into.new` for renummerering.",
        )
    }

    @Test
    fun `fylkesdeling uten etterfølger er gyldig`() {
        assertThat(
            errors(
                ReformOp.Retire(fylke(30)),
                ReformOp.Derive(fylke(31), fylke(30), fylkesdata),
                ReformOp.Derive(fylke(32), fylke(30), fylkesdata),
            ),
        ).isEmpty()
    }

    @Test
    fun `fylke som utgår kan ikke ha etterfølger`() {
        assertRule(
            errors(
                ReformOp.Retire(fylke(30), successor = fylke(31)),
                ReformOp.Derive(fylke(31), fylke(30), fylkesdata),
            ),
            "RetireHasSuccessor",
        )
    }

    @Test
    fun `nytt fylke krever Fylkesdata`() {
        assertThat(errors(ReformOp.Create(fylke(33), fylkesdata))).isEmpty()
        assertRule(errors(ReformOp.Derive(fylke(31), fylke(30), null), ReformOp.RetireTo(fylke(30), fylke(31))), "WrongPayload")
    }

    @Test
    fun `kommune som utgår må ha etterfølger`() {
        assertRule(
            errors(
                ReformOp.Retire(kommune(12, 34)),
                ReformOp.Derive(kommune(12, 35), kommune(12, 34), kommunedata()),
                ReformOp.Derive(kommune(12, 36), kommune(12, 34), kommunedata()),
            ),
            "RetireNeedsSuccessor",
        )
    }

    @Test
    fun `kommunedeling med etterfølger blant delene er gyldig`() {
        assertThat(
            errors(
                ReformOp.Retire(kommune(12, 34), successor = kommune(12, 35)),
                ReformOp.Derive(kommune(12, 35), kommune(12, 34), kommunedata()),
                ReformOp.Derive(kommune(12, 36), kommune(12, 34), kommunedata()),
            ),
        ).isEmpty()
    }

    @Test
    fun `etterfølgeren til en kommune kan finnes fra før eller avledes fra en annen kilde`() {
        assertThat(
            errors(
                ReformOp.Retire(kommune(12, 34), successor = kommune(12, 50)),
                ReformOp.Derive(kommune(12, 35), kommune(12, 34), kommunedata()),
            ),
        ).isEmpty()
        assertThat(
            errors(
                ReformOp.Retire(kommune(12, 34), successor = kommune(12, 50)),
                ReformOp.Derive(kommune(12, 35), kommune(12, 34), kommunedata()),
                ReformOp.Retire(kommune(12, 40), successor = kommune(12, 50)),
                ReformOp.Derive(kommune(12, 50), kommune(12, 40), kommunedata()),
                ReformOp.Derive(kommune(12, 51), kommune(12, 40), kommunedata()),
            ),
        ).isEmpty()
    }

    @Test
    fun `ny kommune krever Kommunedata med kommunevåpen`() {
        assertThat(errors(ReformOp.Create(kommune(12, 50), kommunedata()))).isEmpty()
        assertRule(errors(ReformOp.Create(kommune(12, 50), kommunedata(vapen = null))), "MissingMunicipalityCoat")
        assertRule(errors(ReformOp.Create(kommune(12, 50), fylkesdata)), "WrongPayload")
    }

    @Test
    fun `alle mål fra en kommune må ligge i samme fylke`() {
        assertThat(
            errors(
                ReformOp.Retire(kommune(12, 34), successor = kommune(12, 35)),
                ReformOp.Derive(kommune(12, 35), kommune(12, 34), kommunedata()),
                ReformOp.Derive(kommune(12, 36), kommune(12, 34), kommunedata()),
            ),
        ).isEmpty()
        assertRule(
            errors(
                ReformOp.Retire(kommune(12, 34), successor = kommune(12, 35)),
                ReformOp.Derive(kommune(12, 35), kommune(12, 34), kommunedata()),
                ReformOp.Derive(kommune(30, 36), kommune(12, 34), kommunedata()),
            ),
            "TargetsInMultipleCounties",
        )
    }

    @Test
    fun `kommune kan ikke flyttes`() {
        val errors = errors(ReformOp.Move(kommune(12, 34), kommune(12, 50)))
        assertRule(errors, "ActionNotSupported")
        assertThat(errors.single().hint).isEqualTo(
            "Kommune kan ikke flyttes. Bruk `merge` med `into.new` for renummerering.",
        )
    }

    @Test
    fun `veg kan flyttes og deles med identity, men ikke opprettes eller avvikles`() {
        assertThat(errors(ReformOp.Move(veg(12, 34, 100), veg(12, 35, 200)))).isEmpty()
        assertThat(
            errors(
                ReformOp.Move(veg(12, 34, 100), veg(12, 35, 200)),
                ReformOp.Derive(veg(12, 36, 300), veg(12, 34, 100)),
            ),
        ).isEmpty()
        assertRule(errors(ReformOp.Create(veg(12, 35, 200), fylkesdata)), "ActionNotSupported")
        assertRule(errors(ReformOp.Retire(veg(12, 34, 100))), "ActionNotSupported")
        assertRule(errors(ReformOp.RetireTo(veg(12, 34, 100), veg(12, 35, 200))), "ActionNotSupported")
    }

    @Test
    fun `veg kan bare avledes fra en kilde som flyttes`() {
        assertRule(
            errors(ReformOp.Derive(veg(12, 36, 300), veg(12, 34, 100))),
            "DeriveWithoutMove",
        )
    }

    @Test
    fun `alle mål fra en veg må ligge i samme fylke`() {
        assertRule(
            errors(
                ReformOp.Move(veg(12, 34, 100), veg(12, 35, 200)),
                ReformOp.Derive(veg(30, 1, 300), veg(12, 34, 100)),
            ),
            "TargetsInMultipleCounties",
        )
    }

    @Test
    fun `gårdsnummer kan utgå bare med flytting av bruksnumre under`() {
        assertThat(
            errors(
                ReformOp.Retire(serie(34, 40)),
                ReformOp.Move(grunneiendom(34, 40, 1), grunneiendom(35, 60, 1)),
            ),
        ).isEmpty()
        assertRule(errors(ReformOp.Retire(serie(34, 40))), "SeriesRetireWithoutTitleMoves")
    }

    @Test
    fun `gårdsnummer som utgår kan ikke ha etterfølger eller slås sammen`() {
        val flytting = ReformOp.Move(grunneiendom(34, 40, 1), grunneiendom(35, 60, 1))
        assertRule(errors(ReformOp.Retire(serie(34, 40), successor = serie(35, 60)), flytting), "RetireHasSuccessor")
        assertRule(errors(ReformOp.RetireTo(serie(34, 40), serie(35, 60))), "ActionNotSupported")
        assertRule(errors(ReformOp.Create(serie(35, 60), fylkesdata)), "ActionNotSupported")
    }

    @Test
    fun `gårdsnummer kan flyttes`() {
        assertThat(errors(ReformOp.Move(serie(34, 40), serie(35, 60)))).isEmpty()
    }

    @Test
    fun `vegadresse, krets og grunneiendom kan bare flyttes`() {
        assertThat(errors(ReformOp.Move(vegadresse(100, 1), vegadresse(200, 2)))).isEmpty()
        assertThat(errors(ReformOp.Move(grunneiendom(34, 40, 1), grunneiendom(34, 40, 9)))).isEmpty()
        assertRule(errors(ReformOp.Create(vegadresse(200, 2), fylkesdata)), "ActionNotSupported")
        assertRule(errors(ReformOp.Retire(grunneiendom(34, 40, 1))), "ActionNotSupported")
    }

    @Test
    fun `teig flyttes parvis for bnr 0 og 1`() {
        assertThat(
            errors(ReformOp.Move(teig(34, 0, 1), teig(35, 0, 2)), ReformOp.Move(teig(34, 1, 1), teig(35, 1, 2))),
        ).isEmpty()
        assertRule(errors(ReformOp.Move(teig(34, 0, 1), teig(35, 0, 2))), "ParcelNotPaired")
        assertRule(
            errors(ReformOp.Move(teig(34, 0, 1), teig(35, 1, 2)), ReformOp.Move(teig(34, 1, 1), teig(35, 0, 2))),
            "ParcelNotPaired",
        )
    }

    @Test
    fun `teig med matrikkelnummer kan ikke flyttes`() {
        val errors = errors(ReformOp.Move(teig(34, 0, 1, gnr = 5), teig(35, 0, 2, gnr = 5)))
        assertRule(errors, "ParcelHasCadastralNumber")
        assertThat(errors.single().ops).hasSize(1)
    }

    @Test
    fun `kilde og mål må ha samme identtype`() {
        val errors = errors(ReformOp.Move(veg(12, 34, 100), vegadresse(200, 1)))
        assertRule(errors, "TargetTypeMismatch")
        assertThat(errors.single()).isInstanceOf<DomainErr.TargetTypeMismatch>()
    }

    @Test
    fun `identtyper uten sink-støtte avvises`() {
        val kretstype = kretstypeType(Fylkesnummer(12), Kommunenummer.Lopenummer(34), Kretstype("valg"))
        val flyttet = kretstypeType(Fylkesnummer(12), Kommunenummer.Lopenummer(35), Kretstype("valg"))
        assertRule(errors(ReformOp.Move(kretstype, flyttet)), "UnsupportedIdentType")
    }

    @Test
    fun `feil samles`() {
        val errors = errors(
            ReformOp.Move(fylke(30), fylke(31)),
            ReformOp.Create(veg(12, 35, 200), fylkesdata),
        )
        assertThat(errors.map { it.rule }).containsExactly("ActionNotSupported", "ActionNotSupported")
        assertThat(errors.map { it.ops.single() }.size).isEqualTo(2)
        assertThat(errors.first().hint).isNotNull()
    }
}
