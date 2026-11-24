package no.kartverket.komreg.transformation.reform

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Fylkesnavn
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Kretsnummer
import no.kartverket.komreg.core.domain.Kretstype
import no.kartverket.komreg.core.domain.Matrikkelnummer.Bruksnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Festenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Seksjonsnummer
import no.kartverket.komreg.core.domain.TeigId
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.identTypeOf1
import no.kartverket.komreg.integration.spi.identTypeOf3
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.transformation.Endring
import no.kartverket.komreg.transformation.FraEnTilMange
import no.kartverket.komreg.transformation.FraTil
import no.kartverket.komreg.transformation.Fylkeendring
import no.kartverket.komreg.transformation.IdentTransformer
import no.kartverket.komreg.transformation.Kommuneendring
import no.kartverket.komreg.transformation.Kretsendring
import no.kartverket.komreg.transformation.Matrikkelenhetendring
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.Teigendring
import no.kartverket.komreg.transformation.Vegadresseendring
import no.kartverket.komreg.transformation.Vegendring
import no.kartverket.komreg.transformation.dummyKommune
import no.kartverket.komreg.transformation.mapInput

/**
 * Et kjent avvik mellom dagens kjøring og ny kjøring. [avvik] er raden i avvikstabellen i
 * `docs/handoff-1-generator.md`.
 */
private class Avvik(val avvik: String, val bareIDag: Set<Row> = emptySet(), val bareNå: Set<Row> = emptySet())

private class Scenario(
    val navn: String,
    val endringer: suspend () -> List<Endring>,
    val entiteter: suspend () -> List<Entity>,
    val avvik: suspend (MemoIdFor) -> List<Avvik> = { emptyList() },
)

/**
 * Kjører `IdentTransformer(mapInput(input))` og `ReformTransformer` på de samme entitetene og sammenligner
 * `(id, transformedIdent, transformedAssociatedIdents, resultObject)` som mengder. Alle forskjeller må være
 * deklarert som [Avvik].
 */
class ReformTransformerSammenligningTest : FunSpec({
    val dato = LocalDate(2024, 1, 1)
    val kommuner = listOf(
        dummyKommune(3, 6), dummyKommune(3, 7), dummyKommune(5, 3), dummyKommune(5, 7), dummyKommune(5, 8),
        dummyKommune(12, 35), dummyKommune(12, 36), dummyKommune(31, 1), dummyKommune(80, 99),
    )
    val fylker = listOf(31L, 32L, 33L, 34L).map { Fylke(fnr(it), Fylkesnavn("Fylke $it"), null) }

    // Kommune- og fylkeentiteter har egne IdType-er, slik at ID-en til en Create er den samme som dagens
    // `idFor(entity.id.type, mål)`.
    fun kommuneEntity(f: Long, l: Int, ident: Ident) = Entity(kommuneId(f * 100 + l), ident)
    fun fylkeEntity(f: Long, ident: Ident) = Entity(fylkeId(f), ident)

    val scenarier = listOf(
        Scenario(
            "kommune flyttes til annet fylke",
            { listOf(Kommuneendring(FraEnTilMange(fnr(2), listOf(fnr(3))), FraEnTilMange(klnr(5), listOf(klnr(6))))) },
            {
                listOf(
                    kommuneEntity(2, 5, kommune(2, 5)),
                    Entity(fooId(1), bygning(2, 5, 1), setOf(kommune(2, 5))),
                    Entity(fooId(2), veg(2, 5, 100)),
                    Entity(fooId(3), matrikkelenhet(2, 5, 1, 1), setOf(gnr(2, 5, 1))),
                    Entity(fooId(4), bygning(9, 9, 1)),
                )
            },
        ),
        Scenario(
            "sammenslåing til ny kommune, én kilde merket som sammenslåing",
            { listOf(
                Kommuneendring(FraEnTilMange(fnr(5), listOf(fnr(5))), FraEnTilMange(klnr(1), listOf(klnr(3)))),
                Kommuneendring(
                    FraEnTilMange(fnr(5), listOf(fnr(5))), FraEnTilMange(klnr(2), listOf(klnr(3))),
                    sammenslaaing = true,
                ),
            ) },
            {
                listOf(
                    kommuneEntity(5, 1, kommune(5, 1)),
                    kommuneEntity(5, 2, kommune(5, 2)),
                    Entity(fooId(1), bygning(5, 2, 1)),
                )
            },
        ),
        Scenario(
            "sammenslåing til eksisterende kommune, alle merket som sammenslåing",
            { listOf(
                Kommuneendring(
                    FraEnTilMange(fnr(5), listOf(fnr(5))), FraEnTilMange(klnr(1), listOf(klnr(3))),
                    sammenslaaing = true,
                ),
                Kommuneendring(
                    FraEnTilMange(fnr(5), listOf(fnr(5))), FraEnTilMange(klnr(2), listOf(klnr(3))),
                    sammenslaaing = true,
                ),
            ) },
            { listOf(kommuneEntity(5, 1, kommune(5, 1)), kommuneEntity(5, 2, kommune(5, 2))) },
        ),
        Scenario(
            "kommunedeling",
            { listOf(
                Kommuneendring(FraEnTilMange(fnr(5), listOf(fnr(5))), FraEnTilMange(klnr(5), listOf(klnr(7), klnr(8)))),
                Matrikkelenhetendring(FraTil(fnr(5), fnr(5)), FraTil(klnr(5), klnr(8)), Gardsnummer(1), Gardsnummer(1)),
            ) },
            {
                listOf(
                    kommuneEntity(5, 5, kommune(5, 5)),
                    Entity(fooId(1), bygning(5, 5, 1)),
                    Entity(fooId(2), matrikkelenhet(5, 5, 1, 1)),
                    Entity(fooId(3), matrikkelenhet(5, 5, 2, 1)),
                    Entity(fooId(4), associatedIdents = setOf(kommune(5, 5))),
                )
            },
            {
                listOf(
                    Avvik(
                        "Assosierte identer ved deling",
                        bareIDag = setOf(Row(fooId(4), null, setOf(kommune(5, 7), kommune(5, 8)), null)),
                        bareNå = setOf(Row(fooId(4), null, setOf(kommune(5, 7)), null)),
                    ),
                )
            },
        ),
        Scenario(
            "fylkedeling",
            { listOf(Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(31), fnr(32))))) },
            {
                listOf(
                    fylkeEntity(30, fylke(30)),
                    kommuneEntity(30, 1, kommune(30, 1)),
                    Entity(fooId(1), associatedIdents = setOf(fylke(30))),
                )
            },
            {
                listOf(
                    Avvik(
                        "Deling uten etterfølger",
                        bareIDag = setOf(Row(fylkeId(30), Ident.Empty, null, null)),
                        bareNå = setOf(Row(fylkeId(30), null, null, null)),
                    ),
                    Avvik(
                        "Fylkeregler og kommunevise entiteter",
                        bareNå = setOf(
                            Row(kommuneId(3001), identTypeOf1<Kommunenummer.Lopenummer>()(klnr(1)), null, null),
                        ),
                    ),
                    Avvik(
                        "Assosierte identer ved deling",
                        bareIDag = setOf(Row(fooId(1), null, setOf(Ident.Empty, fylke(31), fylke(32)), null)),
                        bareNå = setOf(Row(fooId(1), null, emptySet(), null)),
                    ),
                )
            },
        ),
        Scenario(
            "fylkesammenslåing og fylke 1:1",
            { listOf(
                Fylkeendring(FraEnTilMange(fnr(30), listOf(fnr(33)))),
                Fylkeendring(FraEnTilMange(fnr(31), listOf(fnr(33))), sammenslaaing = true),
                Fylkeendring(FraEnTilMange(fnr(32), listOf(fnr(34)))),
            ) },
            {
                listOf(
                    fylkeEntity(30, fylke(30)),
                    fylkeEntity(31, fylke(31)),
                    fylkeEntity(32, fylke(32)),
                    Entity(fooId(1), associatedIdents = setOf(fylke(30), fylke(32))),
                )
            },
        ),
        Scenario(
            "veg flyttes",
            { listOf(
                Vegendring(
                    FraEnTilMange(fnr(12), listOf(fnr(12))),
                    FraEnTilMange(klnr(34), listOf(klnr(35))),
                    FraEnTilMange(Adressekode(100), listOf(Adressekode(200))),
                ),
            ) },
            {
                listOf(
                    Entity(fooId(1), veg(12, 34, 100)),
                    Entity(fooId(2), vegadresse(12, 34, 100, 5)),
                    Entity(fooId(3), veg(12, 34, 101)),
                )
            },
        ),
        Scenario(
            "vegdeling, vegadresse med og uten egen regel",
            { listOf(
                Vegendring(
                    FraEnTilMange(fnr(12), listOf(fnr(12))),
                    FraEnTilMange(klnr(34), listOf(klnr(35), klnr(36))),
                    FraEnTilMange(Adressekode(100), listOf(Adressekode(100))),
                ),
                Vegadresseendring(
                    FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(36)),
                    FraTil(Adressekode(100), Adressekode(100)), FraTil(Adressenummernummer(5), Adressenummernummer(5)),
                ),
            ) },
            {
                listOf(
                    Entity(fooId(1), veg(12, 34, 100)),
                    Entity(fooId(2), vegadresse(12, 34, 100, 5)),
                    Entity(fooId(3), vegadresse(12, 34, 100, 6)),
                )
            },
            {
                listOf(
                    Avvik(
                        "Vegdeling, vegadresser uten egen regel",
                        bareIDag = setOf(
                            Row(fooId(3), identTypeOf1<Adressenummernummer>()(Adressenummernummer(6)), null, null),
                        ),
                        bareNå = setOf(Row(fooId(3), vegadresse(12, 35, 100, 6), null, null)),
                    ),
                )
            },
        ),
        Scenario(
            "vegadresse flyttes",
            { listOf(
                Vegadresseendring(
                    FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(34)),
                    FraTil(Adressekode(100), Adressekode(200)), FraTil(Adressenummernummer(5), Adressenummernummer(7)),
                ),
            ) },
            { listOf(Entity(fooId(1), vegadresse(12, 34, 100, 5)), Entity(fooId(2), vegadresse(12, 34, 100, 6))) },
        ),
        Scenario(
            "krets flyttes med kommunen og alene",
            { listOf(
                Kommuneendring(FraEnTilMange(fnr(12), listOf(fnr(12))), FraEnTilMange(klnr(34), listOf(klnr(35)))),
                Kretsendring(
                    FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(36)),
                    FraTil(Kretsnummer(1), Kretsnummer(2)), FraTil(Kretstype("G"), Kretstype("G")),
                ),
            ) },
            {
                listOf(
                    Entity(fooId(1), krets(12, 34, "G", 1)),
                    Entity(fooId(2), krets(12, 34, "G", 3)),
                    Entity(fooId(3), krets(12, 34, "S", 1)),
                )
            },
        ),
        Scenario(
            "teig flyttes",
            { listOf(
                Teigendring(FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)), FraTil(TeigId(7), TeigId(8))),
            ) },
            {
                listOf(
                    Entity(fooId(1), associatedIdents = setOf(teig(12, 34, 0, 0, 7))),
                    Entity(fooId(2), associatedIdents = setOf(teig(12, 34, 0, 1, 7), teig(12, 34, 0, 0, 9))),
                    Entity(fooId(3), associatedIdents = setOf(teig(12, 34, 1, 1, 7))),
                )
            },
            {
                listOf(
                    Avvik(
                        "Teig med matrikkelnummer",
                        bareIDag = setOf(Row(fooId(3), null, setOf(teig(12, 35, 1, 1, 8)), null)),
                    ),
                )
            },
        ),
        Scenario(
            "matrikkelenhet: del kommune, manglende regel for gårdsnummer",
            { listOf(
                Kommuneendring(
                    FraEnTilMange(fnr(12), listOf(fnr(12))),
                    FraEnTilMange(klnr(34), listOf(klnr(35), klnr(36))),
                ),
                Matrikkelenhetendring(FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)), Gardsnummer(1), Gardsnummer(1)),
            ) },
            {
                listOf(
                    kommuneEntity(12, 34, kommune(12, 34)),
                    Entity(fooId(1), matrikkelenhet(12, 34, 1, 1)),
                    Entity(fooId(2), matrikkelenhet(12, 34, 2, 1)),
                    Entity(fooId(3), associatedIdents = setOf(grunneiendom(12, 34, 2, 1))),
                )
            },
        ),
        Scenario(
            "matrikkelenhet: del gårdsnummerserie, komplette parametere",
            { listOf(
                Matrikkelenhetendring(
                    FraTil(fnr(10), fnr(10)), FraTil(klnr(20), klnr(90)), Gardsnummer(40), Gardsnummer(60),
                    mapOf(
                        Bruksnummer(1) to grunneiendom(80, 99, 70, 1),
                        Bruksnummer(2) to grunneiendom(10, 90, 60, 2),
                    ),
                ),
            ) },
            {
                listOf(
                    Entity(fooId(1), matrikkelenhet(10, 20, 40, 1)),
                    Entity(fooId(2), matrikkelenhet(10, 20, 40, 2)),
                )
            },
        ),
        Scenario(
            "matrikkelenhet: del gårdsnummerserie, ikke komplette parametere",
            { listOf(
                Matrikkelenhetendring(
                    FraTil(fnr(10), fnr(10)), FraTil(klnr(20), klnr(90)), Gardsnummer(40), Gardsnummer(60),
                    mapOf(Bruksnummer(1) to grunneiendom(80, 99, 70, 1)),
                ),
            ) },
            {
                listOf(
                    Entity(fooId(1), matrikkelenhet(10, 20, 40, 1)),
                    Entity(fooId(2), matrikkelenhet(10, 20, 40, 2)),
                )
            },
            {
                val utenSerie = identTypeOf3<Bruksnummer, Festenummer, Seksjonsnummer>()
                listOf(
                    Avvik(
                        "Gårdsnummerserie",
                        bareIDag = setOf(
                            Row(
                                fooId(2),
                                utenSerie(Bruksnummer(2), Festenummer(0), Seksjonsnummer(0)),
                                null,
                                null,
                            ),
                        ),
                        bareNå = setOf(Row(fooId(2), matrikkelenhet(10, 90, 60, 2), null, null)),
                    ),
                )
            },
        ),
        Scenario(
            "matrikkelenhet: flytt bruksnummer, bevar gårdsnummer",
            { listOf(
                Matrikkelenhetendring(
                    FraTil(fnr(10), fnr(10)), FraTil(klnr(20), klnr(20)), Gardsnummer(40), Gardsnummer(40),
                    mapOf(Bruksnummer(1) to grunneiendom(10, 30, 50, 4)),
                ),
            ) },
            {
                listOf(
                    Entity(fooId(1), matrikkelenhet(10, 20, 40, 1), setOf(grunneiendom(10, 20, 40, 1))),
                    Entity(fooId(2), matrikkelenhet(10, 20, 40, 2)),
                )
            },
        ),
        Scenario(
            "TransformEntityTest: kommuneflytting, kommunedeling og vegdeling",
            { listOf(
                Kommuneendring(FraEnTilMange(fnr(2), listOf(fnr(3))), FraEnTilMange(klnr(5), listOf(klnr(6)))),
                Kommuneendring(FraEnTilMange(fnr(5), listOf(fnr(5))), FraEnTilMange(klnr(5), listOf(klnr(7), klnr(8)))),
                Vegendring(
                    FraEnTilMange(fnr(2), listOf(fnr(3))),
                    FraEnTilMange(klnr(5), listOf(klnr(6), klnr(7))),
                    FraEnTilMange(Adressekode(2500), listOf(Adressekode(2500), Adressekode(2500))),
                ),
            ) },
            {
                listOf(
                    kommuneEntity(2, 5, kommune(2, 5)),
                    kommuneEntity(5, 5, kommune(5, 5)),
                    kommuneEntity(10, 50, kommune(10, 50)),
                    Entity(fooId(1), veg(2, 5, 2500)),
                    Entity(fooId(2), associatedIdents = setOf(gnr(2, 5, 1), gnr(10, 15, 1))),
                    Entity(fooId(3), bygning(2, 5, 1), setOf(veg(2, 5, 2500))),
                )
            },
            {
                listOf(
                    Avvik(
                        "Assosierte identer ved deling",
                        bareIDag = setOf(
                            Row(fooId(3), bygning(3, 6, 1), setOf(veg(3, 6, 2500), veg(3, 7, 2500)), null),
                        ),
                        bareNå = setOf(Row(fooId(3), bygning(3, 6, 1), setOf(veg(3, 6, 2500)), null)),
                    ),
                )
            },
        ),
    )

    scenarier.forEach { s ->
        test(s.navn) {
            val input = Reguleringsinput("sammenligning", dato, s.endringer(), fylker, kommuner)
            val entiteter = s.entiteter()
            val idFor = MemoIdFor()

            val dagens = IdentTransformer(mapInput(input))
            val iDag = entiteter.flatMapTo(HashSet()) { e -> dagens.transform(e, idFor::invoke).orEmpty().map { it.row() } }

            val ny = transformerOf(input.toReformOps())
            val nå = entiteter.flatMapTo(HashSet()) { e -> ny.transform(e, idFor::invoke).map { it.row() } } +
                ny.creates(idFor::invoke).map { it.row() }

            val avvik = s.avvik(idFor)
            withClue("Avvik: ${avvik.map { it.avvik }}") {
                (iDag - nå) shouldBe avvik.flatMapTo(HashSet()) { it.bareIDag }
                (nå - iDag) shouldBe avvik.flatMapTo(HashSet()) { it.bareNå }
            }
        }
    }
})
