package no.kartverket.komreg.transformation.reform

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Fylkesnavn
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Kretsnummer
import no.kartverket.komreg.core.domain.Kretstype
import no.kartverket.komreg.core.domain.Matrikkelnummer.Bruksnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer
import no.kartverket.komreg.core.domain.TeigId
import no.kartverket.komreg.integration.spi.identTypeOf1
import no.kartverket.komreg.integration.spi.identTypeOf2
import no.kartverket.komreg.integration.spi.identTypeOf3
import no.kartverket.komreg.integration.spi.identTypeOf4
import no.kartverket.komreg.integration.spi.invoke
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
import no.kartverket.komreg.transformation.dummyKommune
import no.kartverket.komreg.transformation.getTeigIdentType

class ReformMappingTest : FunSpec({
    val dato = LocalDate(2024, 1, 1)
    val fylker = listOf(30L, 31L, 32L, 33L).map { Fylke(Fylkesnummer(it), Fylkesnavn("Fylke $it"), null) }
    val kommuner = listOf(12 to 35, 12 to 36, 12 to 50, 31 to 1, 33 to 5).map { (f, k) -> dummyKommune(f, k) }

    fun input(vararg endringer: Endring) = Reguleringsinput("reg", dato, endringer.toList(), fylker, kommuner)

    fun fnr(n: Long) = Fylkesnummer(n)
    fun klnr(n: Int) = Kommunenummer.Lopenummer(n.toByte())
    suspend fun fylke(n: Long) = identTypeOf1<Fylkesnummer>()(fnr(n))
    suspend fun kommune(f: Long, k: Int) = identTypeOf2<Fylkesnummer, Kommunenummer.Lopenummer>()(fnr(f), klnr(k))
    fun serie(f: Long, k: Int, gnr: Int) = Matrikkelenhet.GardsnummerserieIdent(fnr(f), klnr(k), Gardsnummer(gnr))
    fun grunneiendom(f: Long, k: Int, gnr: Int, bnr: Short) = serie(f, k, gnr) / Bruksnummer(bnr)

    suspend fun Reguleringsinput.validOps(): List<ReformOp> {
        val ops = toReformOps()
        ReformClaims.validate(ops).isRight() shouldBe true
        return ops
    }

    test("fylke én til én gir RetireTo og Derive") {
        val ops = input(Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(31))))).validOps()

        ops shouldContainExactly listOf(
            ReformOp.RetireTo(fylke(30), fylke(31)),
            ReformOp.Derive(fylke(31), from = fylke(30), Fylkesdata("Fylke 31")),
        )
    }

    test("fylkessammenslåing oppretter målet én gang") {
        val ops = input(
            Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(33)))),
            Fylkeendring(FraEnTilMange(fnr(31), listOf(fnr(33))), sammenslaaing = true),
        ).validOps()

        ops shouldContainExactly listOf(
            ReformOp.Create(fylke(33), Fylkesdata("Fylke 33")),
            ReformOp.RetireTo(fylke(30), fylke(33)),
            ReformOp.RetireTo(fylke(31), fylke(33)),
        )
    }

    test("sammenslåing der alle kildeendringer er merket, oppretter ikke målet") {
        val ops = input(
            Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(33))), sammenslaaing = true),
            Kommuneendring(FraEnTilMange(fnr(12), listOf(fnr(12))), FraEnTilMange(klnr(34), listOf(klnr(50))), sammenslaaing = true),
        ).validOps()

        ops shouldContainExactly listOf(
            ReformOp.RetireTo(fylke(30), fylke(33)),
            ReformOp.RetireTo(kommune(12, 34), kommune(12, 50)),
        )
        ReformClaims.validate(ops).getOrNull()!!.requiredExistingTargets shouldBe setOf(fylke(33), kommune(12, 50))
    }

    test("sammenslåing uten flagg oppretter også målet én gang") {
        input(
            Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(33)))),
            Fylkeendring(FraEnTilMange(fnr(31), listOf(fnr(33)))),
        ).validOps() shouldContainExactly listOf(
            ReformOp.Create(fylke(33), Fylkesdata("Fylke 33")),
            ReformOp.RetireTo(fylke(30), fylke(33)),
            ReformOp.RetireTo(fylke(31), fylke(33)),
        )
    }

    test("fylkesdeling gir Retire uten etterfølger og Derive per mål") {
        val ops = input(Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(31), fnr(32))))).validOps()

        ops shouldContainExactly listOf(
            ReformOp.Retire(fylke(30)),
            ReformOp.Derive(fylke(31), from = fylke(30), Fylkesdata("Fylke 31")),
            ReformOp.Derive(fylke(32), from = fylke(30), Fylkesdata("Fylke 32")),
        )
    }

    test("deling og sammenslåing til samme mål gir Create") {
        val ops = input(
            Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(31), fnr(32)))),
            Fylkeendring(FraEnTilMange(fnr(33), listOf(fnr(32))), sammenslaaing = true),
        ).validOps()

        ops shouldContainExactly listOf(
            ReformOp.Create(fylke(32), Fylkesdata("Fylke 32")),
            ReformOp.Retire(fylke(30)),
            ReformOp.Derive(fylke(31), from = fylke(30), Fylkesdata("Fylke 31")),
            ReformOp.RetireTo(fylke(33), fylke(32)),
        )
    }

    test("kommune én til én gir RetireTo og Derive med kommunedata") {
        val ops = input(
            Kommuneendring(FraEnTilMange(fnr(12), listOf(fnr(12))), FraEnTilMange(klnr(34), listOf(klnr(50)))),
        ).validOps()

        ops.size shouldBe 2
        ops[0] shouldBe ReformOp.RetireTo(kommune(12, 34), kommune(12, 50))
        ops[1].shouldBeInstanceOf<ReformOp.Derive>().let {
            it.target shouldBe kommune(12, 50)
            it.from shouldBe kommune(12, 34)
            it.payload.shouldBeInstanceOf<Kommunedata>().ikrafttredelsesdato shouldBe dato
        }
    }

    test("kommunedeling gir Retire med første mål som etterfølger og Derive per mål") {
        val ops = input(
            Kommuneendring(FraEnTilMange(fnr(12), listOf(fnr(12))), FraEnTilMange(klnr(34), listOf(klnr(35), klnr(36)))),
        ).validOps()

        ops.first() shouldBe ReformOp.Retire(kommune(12, 34), successor = kommune(12, 35))
        ops.drop(1).map { it.shouldBeInstanceOf<ReformOp.Derive>().target to it.from } shouldContainExactly
            listOf(kommune(12, 35) to kommune(12, 34), kommune(12, 36) to kommune(12, 34))
    }

    test("matrikkelenhetendring med ny serie gir Move av serien og hvert bruksnummer") {
        val ops = input(
            Matrikkelenhetendring(
                FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)), Gardsnummer(40), Gardsnummer(60),
                mapOf(Bruksnummer(1) to grunneiendom(12, 35, 70, 1)),
            ),
        ).validOps()

        ops shouldContainExactly listOf(
            ReformOp.Move(serie(12, 34, 40), serie(12, 35, 60)),
            ReformOp.Move(grunneiendom(12, 34, 40, 1), grunneiendom(12, 35, 70, 1)),
        )
    }

    test("matrikkelenhetendring der serien bare bytter kommune gir Move") {
        val ops = input(
            Matrikkelenhetendring(FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)), Gardsnummer(1), Gardsnummer(1)),
        ).validOps()

        ops shouldContainExactly listOf(ReformOp.Move(serie(12, 34, 1), serie(12, 35, 1)))
    }

    test("matrikkelenhetendring uten serieendring gir bare bruksnummerregler") {
        val ops = input(
            Matrikkelenhetendring(
                FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(34)), Gardsnummer(40), Gardsnummer(40),
                mapOf(Bruksnummer(1) to grunneiendom(12, 34, 40, 9)),
            ),
        ).validOps()

        ops shouldContainExactly listOf(ReformOp.Move(grunneiendom(12, 34, 40, 1), grunneiendom(12, 34, 40, 9)))
    }

    test("matrikkelenhetendring med bare bruksnummer gir Retire av serien") {
        val ops = input(
            Matrikkelenhetendring(
                FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)), Gardsnummer(40), null,
                mapOf(
                    Bruksnummer(1) to grunneiendom(12, 35, 60, 1),
                    Bruksnummer(2) to grunneiendom(12, 35, 70, 2),
                ),
            ),
        ).validOps()

        ops shouldContainExactly listOf(
            ReformOp.Retire(serie(12, 34, 40)),
            ReformOp.Move(grunneiendom(12, 34, 40, 1), grunneiendom(12, 35, 60, 1)),
            ReformOp.Move(grunneiendom(12, 34, 40, 2), grunneiendom(12, 35, 70, 2)),
        )
    }

    test("matrikkelenhetendring som beholder serien for noen bruksnummer er ugyldig") {
        shouldThrow<IllegalArgumentException> {
            input(
                Matrikkelenhetendring(
                    FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(34)), Gardsnummer(40), Gardsnummer(60),
                    mapOf(Bruksnummer(1) to grunneiendom(12, 34, 40, 1)),
                ),
            ).toReformOps()
        }
    }

    test("vegendring til én gir Move, til flere gir Move til første og Derive for resten") {
        val veg = identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode>()

        input(
            Vegendring(
                FraEnTilMange(fnr(12), listOf(fnr(12))),
                FraEnTilMange(klnr(34), listOf(klnr(35))),
                FraEnTilMange(Adressekode(100), listOf(Adressekode(200))),
            ),
        ).validOps() shouldContainExactly listOf(
            ReformOp.Move(veg(fnr(12), klnr(34), Adressekode(100)), veg(fnr(12), klnr(35), Adressekode(200))),
        )

        input(
            Vegendring(
                FraEnTilMange(fnr(12), listOf(fnr(12))),
                FraEnTilMange(klnr(34), listOf(klnr(35), klnr(36))),
                FraEnTilMange(Adressekode(100), listOf(Adressekode(200), Adressekode(300))),
            ),
        ).validOps() shouldContainExactly listOf(
            ReformOp.Move(veg(fnr(12), klnr(34), Adressekode(100)), veg(fnr(12), klnr(35), Adressekode(200))),
            ReformOp.Derive(veg(fnr(12), klnr(36), Adressekode(300)), from = veg(fnr(12), klnr(34), Adressekode(100))),
        )

        // Felles adressekode for alle mål, og indeksen (ikke kommunen) avgjør koden når kommunen gjentas
        input(
            Vegendring(
                FraEnTilMange(fnr(12), listOf(fnr(12))),
                FraEnTilMange(klnr(34), listOf(klnr(35), klnr(36))),
                FraEnTilMange(Adressekode(100), listOf(Adressekode(100))),
            ),
            Vegendring(
                FraEnTilMange(fnr(12), listOf(fnr(12))),
                FraEnTilMange(klnr(34), listOf(klnr(35), klnr(35))),
                FraEnTilMange(Adressekode(101), listOf(Adressekode(201), Adressekode(202))),
            ),
        ).validOps() shouldContainExactly listOf(
            ReformOp.Move(veg(fnr(12), klnr(34), Adressekode(100)), veg(fnr(12), klnr(35), Adressekode(100))),
            ReformOp.Derive(veg(fnr(12), klnr(36), Adressekode(100)), from = veg(fnr(12), klnr(34), Adressekode(100))),
            ReformOp.Move(veg(fnr(12), klnr(34), Adressekode(101)), veg(fnr(12), klnr(35), Adressekode(201))),
            ReformOp.Derive(veg(fnr(12), klnr(35), Adressekode(202)), from = veg(fnr(12), klnr(34), Adressekode(101))),
        )
    }

    test("vegadresse- og kretsendring gir Move") {
        val vegadresse = identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode, Adressenummernummer>()
        val krets = identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Kretstype, Kretsnummer>()

        input(
            Vegadresseendring(
                FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)),
                FraTil(Adressekode(100), Adressekode(200)), FraTil(Adressenummernummer(1), Adressenummernummer(2)),
            ),
            Kretsendring(
                FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)),
                FraTil(Kretsnummer(1), Kretsnummer(2)), FraTil(Kretstype("valg"), Kretstype("valg")),
            ),
        ).validOps() shouldContainExactly listOf(
            ReformOp.Move(
                vegadresse(fnr(12), klnr(34), Adressekode(100), Adressenummernummer(1)),
                vegadresse(fnr(12), klnr(35), Adressekode(200), Adressenummernummer(2)),
            ),
            ReformOp.Move(
                krets(fnr(12), klnr(34), Kretstype("valg"), Kretsnummer(1)),
                krets(fnr(12), klnr(35), Kretstype("valg"), Kretsnummer(2)),
            ),
        )
    }

    test("teigendring gir Move med full nøkkel for bnr 0 og 1") {
        val teig = getTeigIdentType()
        suspend fun key(k: Int, bnr: Short, id: Long) = teig(
            fnr(12), klnr(k), Gardsnummer(0), Bruksnummer(bnr),
            no.kartverket.komreg.core.domain.Matrikkelnummer.Festenummer(0),
            no.kartverket.komreg.core.domain.Matrikkelnummer.Seksjonsnummer(0),
            TeigId(id),
        )

        input(
            Teigendring(FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)), FraTil(TeigId(1), TeigId(2))),
        ).validOps() shouldContainExactly listOf(
            ReformOp.Move(key(34, 0, 1), key(35, 0, 2)),
            ReformOp.Move(key(34, 1, 1), key(35, 1, 2)),
        )
    }

    test("typisk kommunedeling med mer spesifikke regler under kommunen validerer") {
        val ops = input(
            Kommuneendring(FraEnTilMange(fnr(12), listOf(fnr(12))), FraEnTilMange(klnr(34), listOf(klnr(35), klnr(36)))),
            Matrikkelenhetendring(FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)), Gardsnummer(1), Gardsnummer(1)),
            Matrikkelenhetendring(FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(36)), Gardsnummer(2), Gardsnummer(2)),
            Teigendring(FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(36)), FraTil(TeigId(1), TeigId(1))),
            Kretsendring(
                FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)),
                FraTil(Kretsnummer(1), Kretsnummer(1)), FraTil(Kretstype("valg"), Kretstype("valg")),
            ),
            Vegendring(
                FraEnTilMange(fnr(12), listOf(fnr(12))),
                FraEnTilMange(klnr(34), listOf(klnr(35), klnr(36))),
                FraEnTilMange(Adressekode(100), listOf(Adressekode(100), Adressekode(100))),
            ),
            Vegadresseendring(
                FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(36)),
                FraTil(Adressekode(100), Adressekode(100)), FraTil(Adressenummernummer(1), Adressenummernummer(1)),
            ),
        ).validOps()

        ops.filterIsInstance<ReformOp.Create>() shouldBe emptyList()
        ops.filterIsInstance<ReformOp.Derive>().map { it.target } shouldContainExactlyInAnyOrder listOf(
            kommune(12, 35),
            kommune(12, 36),
            identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode>()(fnr(12), klnr(36), Adressekode(100)),
        )
    }

    test("typisk fylkesdeling med kommuneflytting validerer") {
        input(
            Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(31), fnr(33)))),
            Kommuneendring(FraEnTilMange(fnr(30), listOf(fnr(31))), FraEnTilMange(klnr(1), listOf(klnr(1)))),
            Kommuneendring(FraEnTilMange(fnr(30), listOf(fnr(33))), FraEnTilMange(klnr(7), listOf(klnr(5)))),
        ).validOps()
    }
})
