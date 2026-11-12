package no.kartverket.komreg.routes

import arrow.core.Either
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Fylkesnavn
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Koordinat
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.core.domain.Kommunenavn
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Kretsnummer
import no.kartverket.komreg.core.domain.Kretstype
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Bruksnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer
import no.kartverket.komreg.core.domain.TeigId
import no.kartverket.komreg.integration.spi.KommuneService
import no.kartverket.komreg.integration.spi.identTypeOf1
import no.kartverket.komreg.integration.spi.identTypeOf2
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
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.ReformOp
import no.kartverket.komreg.transformation.reform.toReformOps

class ReformClaimsTilReguleringTest : FunSpec({
    val dato = LocalDate(2024, 1, 1)
    val fylker = listOf(30L, 31L, 32L, 33L).map { Fylke(Fylkesnummer(it), Fylkesnavn("Fylke $it"), null) }
    val kommuner = listOf(12 to 34, 12 to 35, 12 to 36, 12 to 50, 31 to 1, 33 to 5, 30 to 1, 30 to 7).map { (f, k) -> kommune(f, k) }

    fun kommuneService() = mockk<KommuneService> {
        every { findAlleFylker() } returns fylker
        every { findAlleKommuner() } returns kommuner
    }

    fun input(vararg endringer: Endring) = Reguleringsinput("reg", dato, endringer.toList(), fylker, kommuner)

    fun fnr(n: Long) = Fylkesnummer(n)
    fun klnr(n: Int) = Kommunenummer.Lopenummer(n.toByte())
    suspend fun fylkeIdent(n: Long) = identTypeOf1<Fylkesnummer>()(fnr(n))
    suspend fun kommuneIdent(f: Long, k: Int) = identTypeOf2<Fylkesnummer, Kommunenummer.Lopenummer>()(fnr(f), klnr(k))
    fun serie(f: Long, k: Int, gnr: Int) = Matrikkelenhet.GardsnummerserieIdent(fnr(f), klnr(k), Gardsnummer(gnr))
    fun grunneiendom(f: Long, k: Int, gnr: Int, bnr: Short) = serie(f, k, gnr) / Bruksnummer(bnr)

    suspend fun ReformClaims.regulering() = toReguleringDTO("reg", "Regulering", dato, kommuneService())

    // Reglene fra en gyldig regulering skal gi en regulering med samme transformasjon
    suspend fun roundtrip(input: Reguleringsinput) {
        val ops = input.toReformOps()
        val claims = ReformClaims.validate(ops).getOrNull()!!

        val regulering = claims.regulering()

        val dto = regulering.shouldBeInstanceOf<Either.Right<Regulering>>().value
        dto.toReguleringsinput().toReformOps().toSet() shouldBe ops.toSet()
    }

    suspend fun ReformClaims.umappbare() = regulering().shouldBeInstanceOf<Either.Left<*>>().value.let {
        @Suppress("UNCHECKED_CAST")
        (it as arrow.core.NonEmptyList<Umappbar>)
    }

    context("roundtrip") {
        test("fylke én til én, sammenslåing og deling") {
            roundtrip(input(Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(31))))))
            roundtrip(
                input(
                    Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(33)))),
                    Fylkeendring(FraEnTilMange(fnr(31), listOf(fnr(33))), sammenslaaing = true),
                ),
            )
            roundtrip(input(Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(31), fnr(32))))))
        }

        test("sammenslåing inn i eksisterende mål") {
            roundtrip(
                input(
                    Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(33))), sammenslaaing = true),
                    Kommuneendring(
                        FraEnTilMange(fnr(12), listOf(fnr(12))),
                        FraEnTilMange(klnr(34), listOf(klnr(50))),
                        sammenslaaing = true,
                    ),
                ),
            )
        }

        test("kommune én til én og deling") {
            roundtrip(
                input(Kommuneendring(FraEnTilMange(fnr(12), listOf(fnr(12))), FraEnTilMange(klnr(34), listOf(klnr(50))))),
            )
            roundtrip(
                input(
                    Kommuneendring(FraEnTilMange(fnr(12), listOf(fnr(12))), FraEnTilMange(klnr(34), listOf(klnr(35), klnr(36)))),
                ),
            )
        }

        test("matrikkelenhet, veg, vegadresse, krets og teig") {
            roundtrip(
                input(
                    Kommuneendring(FraEnTilMange(fnr(12), listOf(fnr(12))), FraEnTilMange(klnr(34), listOf(klnr(35), klnr(36)))),
                    Matrikkelenhetendring(
                        FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)), Gardsnummer(40), Gardsnummer(60),
                        mapOf(Bruksnummer(1) to grunneiendom(12, 35, 70, 1)),
                    ),
                    Matrikkelenhetendring(
                        FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)), Gardsnummer(41), null,
                        mapOf(Bruksnummer(1) to grunneiendom(12, 35, 61, 1), Bruksnummer(2) to grunneiendom(12, 35, 71, 2)),
                    ),
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
                ),
            )
        }

        test("fylkesdeling med kommuneflytting") {
            roundtrip(
                input(
                    Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(31), fnr(33)))),
                    Kommuneendring(FraEnTilMange(fnr(30), listOf(fnr(31))), FraEnTilMange(klnr(1), listOf(klnr(1)))),
                    Kommuneendring(FraEnTilMange(fnr(30), listOf(fnr(33))), FraEnTilMange(klnr(7), listOf(klnr(5)))),
                ),
            )
        }
    }

    context("innhold") {
        test("navn på nye kommuner får store bokstaver og felt som mangler i payloaden blir tomme") {
            val claims = ReformClaims.validate(
                input(Kommuneendring(FraEnTilMange(fnr(12), listOf(fnr(12))), FraEnTilMange(klnr(34), listOf(klnr(50))))).toReformOps(),
            ).getOrNull()!!

            val regulering = claims.regulering().getOrNull()!!

            val endring = regulering.endringer.single()
            endring.type shouldBe "kommune"
            endring.utgåendeKommuner.single().navn shouldBe "Original 12-34"
            endring.nyeKommuner.single().navn shouldBe "ORIGINAL 12-50"
            endring.nyeKommuner.single().gyldigTilDato shouldBe null
        }

        test("divergerende ikrafttredelsesdato justeres til reguleringens dato") {
            val ops = input(
                Kommuneendring(FraEnTilMange(fnr(12), listOf(fnr(12))), FraEnTilMange(klnr(34), listOf(klnr(50)))),
            ).toReformOps().map { op ->
                if (op is ReformOp.Derive && op.payload is Kommunedata) {
                    op.copy(payload = (op.payload as Kommunedata).copy(ikrafttredelsesdato = LocalDate(2030, 5, 5)))
                } else {
                    op
                }
            }

            val regulering = ReformClaims.validate(ops).getOrNull()!!.regulering()

            regulering.isRight() shouldBe true
        }

        test("OrThrow kaster med alle feilene") {
            val claims = ReformClaims.validate(listOf(ReformOp.Retire(kommuneIdent(12, 34)))).getOrNull()!!

            shouldThrow<UmappbarReformException> {
                claims.toReguleringDTOOrThrow("reg", "Regulering", dato, kommuneService())
            }.feil shouldHaveSize 1
        }
    }

    context("umappbart") {
        test("Move av fylke og kommune") {
            val claims = ReformClaims.validate(
                listOf(ReformOp.Move(fylkeIdent(30), fylkeIdent(31)), ReformOp.Move(kommuneIdent(12, 34), kommuneIdent(12, 35))),
            ).getOrNull()!!

            claims.umappbare().map { it::class } shouldBe List(2) { Umappbar.IngenDtoEkvivalent::class }
        }

        test("kommunedeling med Retire uten etterfølger") {
            val claims = ReformClaims.validate(listOf(ReformOp.Retire(kommuneIdent(12, 34)))).getOrNull()!!

            claims.umappbare().single().shouldBeInstanceOf<Umappbar.IngenDtoEkvivalent>()
        }

        test("kommunedeling med eksisterende etterfølger") {
            val claims = ReformClaims.validate(
                listOf(
                    ReformOp.Retire(kommuneIdent(12, 34), successor = kommuneIdent(12, 35)),
                    ReformOp.Derive(kommuneIdent(12, 99), from = kommuneIdent(12, 34), payload = kommunedata()),
                ),
            ).getOrNull()!!

            claims.umappbare().single().shouldBeInstanceOf<Umappbar.IngenDtoEkvivalent>()
                .grunn shouldContain "etterfølgeren ikke opprettes av delingen"
        }

        test("kommunedeling med etterfølger som er avledet fra en annen kilde") {
            val claims = ReformClaims.validate(
                listOf(
                    ReformOp.Retire(kommuneIdent(12, 34), successor = kommuneIdent(12, 97)),
                    ReformOp.Derive(kommuneIdent(12, 97), from = kommuneIdent(12, 34), payload = kommunedata()),
                    ReformOp.Derive(kommuneIdent(12, 98), from = kommuneIdent(12, 34), payload = kommunedata()),
                    ReformOp.Retire(kommuneIdent(12, 35), successor = kommuneIdent(12, 97)),
                    ReformOp.Derive(kommuneIdent(12, 96), from = kommuneIdent(12, 35), payload = kommunedata()),
                ),
            ).getOrNull()!!

            val umappbare = claims.umappbare()
            umappbare.single().shouldBeInstanceOf<Umappbar.IngenDtoEkvivalent>().grunn shouldContain
                "etterfølgeren ikke opprettes av delingen"
        }

        test("Retire av serie uten flytting av bruksnummer") {
            val claims = ReformClaims.validate(listOf(ReformOp.Retire(serie(12, 34, 40)))).getOrNull()!!

            claims.umappbare().single().shouldBeInstanceOf<Umappbar.IngenDtoEkvivalent>()
        }

        test("Retire av teig og grunneiendom") {
            val claims = ReformClaims.validate(listOf(ReformOp.Retire(grunneiendom(12, 34, 40, 1)))).getOrNull()!!

            claims.umappbare().single().shouldBeInstanceOf<Umappbar.IngenDtoEkvivalent>()
        }

        test("mål som verken opprettes eller finnes i KommuneService") {
            val claims = ReformClaims.validate(listOf(ReformOp.RetireTo(kommuneIdent(12, 34), kommuneIdent(12, 99)))).getOrNull()!!

            claims.umappbare().single().shouldBeInstanceOf<Umappbar.FinnesIkke>()
        }

        test("kommunedata uten kommunevåpen") {
            val kommunedata = kommunedata().copy(kommunevapen = null)
            val claims = ReformClaims.validate(
                listOf(
                    ReformOp.RetireTo(kommuneIdent(12, 34), kommuneIdent(12, 99)),
                    ReformOp.Derive(kommuneIdent(12, 99), from = kommuneIdent(12, 34), payload = kommunedata),
                ),
            ).getOrNull()!!

            claims.umappbare().single().shouldBeInstanceOf<Umappbar.UgyldigPayload>()
        }

        test("utskilling, der kilden fortsetter, har egen grunn") {
            val claims = ReformClaims.validate(
                listOf(ReformOp.Derive(kommuneIdent(12, 99), from = kommuneIdent(12, 34), payload = kommunedata())),
            ).getOrNull()!!

            claims.umappbare().filterIsInstance<Umappbar.IngenDtoEkvivalent>().map { it.grunn } shouldBe
                listOf("Utskilling, der kilden fortsetter, kan ikke uttrykkes i DTO-en")
        }

        test("kortform og create med derivedFrom gir samme DTO") {
            val kilde = kommuneIdent(12, 34)
            val mål = kommuneIdent(12, 99)
            val ops = listOf(
                ReformOp.RetireTo(kilde, mål),
                ReformOp.Derive(mål, from = kilde, payload = kommunedata().copy(navn = "NY")),
            )

            // split.into.new og split.into.existing sammen med create.derivedFrom lowerer til de samme op-ene
            ReformClaims.validate(ops).getOrNull()!!.regulering().shouldBeInstanceOf<Either.Right<Regulering>>()
        }

        test("merge til eksisterende mål sammen med create med derivedFrom fra en av kildene") {
            val mål = kommuneIdent(12, 50)
            val claims = ReformClaims.validate(
                listOf(
                    ReformOp.RetireTo(kommuneIdent(12, 34), mål),
                    ReformOp.RetireTo(kommuneIdent(12, 35), mål),
                    ReformOp.Derive(mål, from = kommuneIdent(12, 34), payload = kommunedata()),
                ),
            ).getOrNull()!!

            // Avvist av ekvivalenssjekken: DTO-en gir Create for målet, ikke Derive
            claims.regulering().shouldBeInstanceOf<Either.Left<*>>()
        }

        test("deling der ett mål opprettes og ikke er avledet fra kilden") {
            val ops = input(
                Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(31), fnr(32)))),
                Fylkeendring(FraEnTilMange(fnr(33), listOf(fnr(32))), sammenslaaing = true),
            ).toReformOps()

            ReformClaims.validate(ops).getOrNull()!!.umappbare().map { it::class }.distinct() shouldBe
                listOf(Umappbar.IngenDtoEkvivalent::class)
        }
    }
})

private fun kommunedata() = Kommunedata(
    navn = "Ny",
    koordinatsystem = Koordinatsystem.UTM32,
    senterpunkt = Koordinat(1.0, 2.0),
    nedsattKonsesjonsgrense = false,
    godkjenteGardsnumre = "1,2,3",
    adresse = null,
    standardRekvirentOrgnummer = null,
    ikrafttredelsesdato = LocalDate(2024, 1, 1),
    kommunevapen = byteArrayOf(1, 2, 3),
)

private fun kommune(fylke: Int, løpenummer: Int) = Kommune(
    kommunenummer = Kommunenummer(Fylkesnummer(fylke.toLong()), Kommunenummer.Lopenummer(løpenummer.toByte())),
    kommunenavn = Kommunenavn("Original $fylke-$løpenummer"),
    gyldigTilDato = null,
    koordinatsystem = Koordinatsystem.UTM32,
    senterpunkt = Koordinat(123.0, 456.0),
    nedsattKonsesjonsgrense = false,
    godkjenteGardsnumre = "1,2,3",
    adresse = null,
    standardRekvirent = null,
    kommunevapen = byteArrayOf(1, 2, 3),
)
