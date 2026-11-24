package no.kartverket.komreg.transformation.reform

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Bygningsnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Bruksnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Festenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Seksjonsnummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Payload
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.integration.spi.identTypeOf1
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.transformation.Endring
import no.kartverket.komreg.transformation.FraEnTilMange
import no.kartverket.komreg.transformation.FraTil
import no.kartverket.komreg.transformation.Kommuneendring
import no.kartverket.komreg.transformation.Matrikkelenhetendring
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.TestIdType
import no.kartverket.komreg.transformation.Vegadresseendring
import no.kartverket.komreg.transformation.Vegendring
import no.kartverket.komreg.transformation.dummyKommune
import no.kartverket.komreg.transformation.getMatrikkelenhetIdentType
import no.kartverket.komreg.transformation.getUnresolvedMatrikkelenhetIdentType
import no.kartverket.komreg.transformation.tilKommunedata

private data class Info(val navn: String) : Payload

/**
 * Tester for [ReformTransformer]. Der svaret er annerledes enn i dag (`IdentTransformer`), viser testnavnet til
 * raden i avvikstabellen i `docs/handoff-1-generator.md` med `[Avvik: <område>]`.
 */
class ReformTransformerTest : FunSpec({
    val dato = LocalDate(2024, 1, 1)

    suspend fun ReformTransformer.run(entity: Entity, idFor: MemoIdFor = MemoIdFor()): List<Transformation> =
        transform(entity, idFor::invoke).onEach {
            it.sourceEntity shouldBe entity
            it.sammenslaaing.shouldBeNull()
        }

    suspend fun ReformTransformer.rows(entity: Entity, idFor: MemoIdFor = MemoIdFor()): List<Row> =
        run(entity, idFor).map { it.row() }

    context("Semantikk, én rad per regel i tabellen") {
        class Case(
            val name: String,
            val ops: suspend () -> List<ReformOp>,
            val key: suspend () -> Ident,
            val expected: suspend () -> Ident?,
        )

        val cases = listOf(
            Case(
                "ingen regel: uendret",
                { reform { move(kommune(50, 1), kommune(50, 2)) } },
                { bygning(12, 34, 1) },
                { bygning(12, 34, 1) },
            ),
            Case(
                "Move, eksakt: t",
                { reform { move(veg(12, 34, 100), veg(12, 35, 200)) } },
                { veg(12, 34, 100) },
                { veg(12, 35, 200) },
            ),
            Case(
                "Move, forelder: t + komponentene under s",
                { reform { move(veg(12, 34, 100), veg(12, 35, 200)) } },
                { vegadresse(12, 34, 100, 7) },
                { vegadresse(12, 35, 200, 7) },
            ),
            Case(
                "RetireTo, eksakt: t",
                { reform { retireTo(kommune(12, 34), kommune(12, 35)) } },
                { kommune(12, 34) },
                { kommune(12, 35) },
            ),
            Case(
                "RetireTo, forelder: t + komponentene under s",
                { reform { retireTo(kommune(12, 34), kommune(13, 35)) } },
                { matrikkelenhet(12, 34, 1, 2) },
                { matrikkelenhet(13, 35, 1, 2) },
            ),
            Case(
                "Retire, eksakt med etterfølger: succ",
                { reform { retire(kommune(12, 34), successor = kommune(12, 35)) } },
                { kommune(12, 34) },
                { kommune(12, 35) },
            ),
            Case(
                "Retire, eksakt uten etterfølger: null",
                { reform { retire(fylke(30)) } },
                { fylke(30) },
                { null },
            ),
            Case(
                "Retire, forelder: delvis nøkkel",
                { reform { retire(kommune(12, 34), successor = kommune(12, 35)) } },
                { bygning(12, 34, 1) },
                { identTypeOf1<Bygningsnummer>()(Bygningsnummer(1)) },
            ),
        )

        cases.forEach { case ->
            test("hovedident: ${case.name}") {
                val key = case.key()
                val expected = case.expected()
                val entity = Entity(fooId(1), key)

                val rows = transformerOf(case.ops()).rows(entity)

                if (expected == key) {
                    rows.shouldBeEmpty()
                } else {
                    rows shouldContainExactlyInAnyOrder listOf(Row(fooId(1), expected, null, null))
                    expected?.type?.shouldBeSameInstanceAs(rows.single().ident!!.type)
                }
            }

            test("assosiert ident: ${case.name}") {
                val key = case.key()
                val expected = case.expected()
                val annen = bygning(60, 1, 99)
                val entity = Entity(fooId(1), ident = annen, associatedIdents = setOf(key, gnr(60, 1, 5)))

                val rows = transformerOf(case.ops()).rows(entity)

                if (expected == key) {
                    rows.shouldBeEmpty()
                } else {
                    // En assosiert ident som får `null`, tas ut av settet.
                    rows shouldContainExactlyInAnyOrder listOf(
                        Row(fooId(1), annen, setOfNotNull(expected, gnr(60, 1, 5)), null),
                    )
                }
            }
        }

        test("Move til forelder med samme type gjenbruker identtypen til entiteten") {
            val entity = Entity(fooId(1), matrikkelenhet(12, 34, 1, 2))
            val transformer = transformerOf(reform { move(gnr(12, 34, 1), gnr(12, 35, 7)) })

            val ident = transformer.rows(entity).single().ident!!

            ident shouldBe matrikkelenhet(12, 35, 7, 2)
            ident.type shouldBeSameInstanceAs getMatrikkelenhetIdentType()
        }

        test("mest spesifikke regel brukes (port av «lengste regel brukes»)") {
            val type = getMatrikkelenhetIdentType()
            val entity = Entity(
                fooId(1),
                type(fnr(12), klnr(34), Gardsnummer(4), Bruksnummer(3), Festenummer(2), Seksjonsnummer(1)),
            )
            val transformer = transformerOf(
                reform {
                    retire(kommune(12, 34), successor = kommune(12, 22))
                    move(gnr(12, 34, 4), gnr(43, 21, 5))
                },
            )

            transformer.rows(entity) shouldBe listOf(
                Row(
                    fooId(1),
                    type(fnr(43), klnr(21), Gardsnummer(5), Bruksnummer(3), Festenummer(2), Seksjonsnummer(1)),
                    null,
                    null,
                ),
            )
        }
    }

    context("Ingen endring og entiteter uten hovedident") {
        val ops = reform { retireTo(kommune(12, 34), kommune(12, 35)) }

        test("uendret entitet gir tom liste") {
            transformerOf(ops).rows(Entity(fooId(1), bygning(12, 36, 1), setOf(gnr(12, 36, 1)))).shouldBeEmpty()
        }

        test("uten hovedident regnes bare de assosierte identene ut") {
            val entity = Entity(fooId(1), associatedIdents = setOf(teig(12, 34, 1, 1, 7), teig(12, 36, 1, 1, 8)))

            transformerOf(ops).rows(entity) shouldBe listOf(
                Row(fooId(1), null, setOf(teig(12, 35, 1, 1, 7), teig(12, 36, 1, 1, 8)), null),
            )
        }

        test("uten hovedident og uendrede assosierte identer gir tom liste") {
            transformerOf(ops).rows(Entity(fooId(1), associatedIdents = setOf(teig(12, 36, 1, 1, 8)))).shouldBeEmpty()
        }

        test("Ident.Empty som hovedident er uendret") {
            transformerOf(ops).rows(Entity(fooId(1), Ident.Empty)).shouldBeEmpty()
        }

        test("associatedIdents = null forblir null") {
            transformerOf(ops).rows(Entity(fooId(1), bygning(12, 34, 1))) shouldBe listOf(
                Row(fooId(1), bygning(12, 35, 1), null, null),
            )
        }
    }

    context("Derive") {
        test("kommunedeling: kilden får etterfølgeren, og hver del avledes med kildens assosierte identer") {
            val k34 = kommune(12, 34)
            val k35 = kommune(12, 35)
            val k36 = kommune(12, 36)
            val transformer = transformerOf(
                reform {
                    retire(k34, successor = k35)
                    derive(k35, from = k34, Info("35"))
                    derive(k36, from = k34, Info("36"))
                },
            )
            val entity = Entity(kommuneId(1234), k34, setOf(gnr(12, 34, 1), gnr(50, 1, 1)))
            val idFor = MemoIdFor()

            val assoc = setOf(identTypeOf1<Gardsnummer>()(Gardsnummer(1)), gnr(50, 1, 1))
            transformer.rows(entity, idFor) shouldContainExactlyInAnyOrder listOf(
                Row(kommuneId(1234), k35, assoc, null),
                Row(idFor(TestIdType.Kommune, k35), k35, assoc, Info("35")),
                Row(idFor(TestIdType.Kommune, k36), k36, assoc, Info("36")),
            )
        }

        test("1:1-erstatning: RetireTo + Derive til samme mål") {
            val k34 = kommune(12, 34)
            val k35 = kommune(12, 35)
            val transformer = transformerOf(
                reform {
                    retireTo(k34, k35)
                    derive(k35, from = k34, Info("35"))
                },
            )
            val idFor = MemoIdFor()

            transformer.rows(Entity(kommuneId(1234), k34), idFor) shouldContainExactlyInAnyOrder listOf(
                Row(kommuneId(1234), k35, null, null),
                Row(idFor(TestIdType.Kommune, k35), k35, null, Info("35")),
            )
        }

        test("utskilling: bare den nye entiteten får en transformasjon [Avvik: Utskilling]") {
            val k34 = kommune(12, 34)
            val k36 = kommune(12, 36)
            val transformer = transformerOf(reform { derive(k36, from = k34, Info("36")) })
            val idFor = MemoIdFor()

            val result = transformer.run(Entity(kommuneId(1234), k34), idFor)

            result.map { it.row() } shouldBe listOf(Row(idFor(TestIdType.Kommune, k36), k36, null, Info("36")))
            (result.single().id != kommuneId(1234)) shouldBe true
        }

        test("Derive gjelder bare eksakt treff på from") {
            val transformer = transformerOf(reform { derive(kommune(12, 36), from = kommune(12, 34), Info("36")) })

            transformer.rows(Entity(fooId(1), bygning(12, 34, 1))).shouldBeEmpty()
        }

        test("Derive uten payload") {
            val v1 = veg(12, 34, 100)
            val v2 = veg(12, 35, 100)
            val idFor = MemoIdFor()

            transformerOf(reform { derive(v2, from = v1) }).rows(Entity(fooId(1), v1), idFor) shouldBe listOf(
                Row(idFor(TestIdType.Foo, v2), v2, null, null),
            )
        }
    }

    context("Create") {
        test("én transformasjon per Create, uansett hvor mange kilder som peker på målet [Avvik: Sammenslåing til ny kommune]") {
            val k35 = kommune(12, 35)
            val transformer = transformerOf(
                reform {
                    create(k35, Info("35"))
                    retireTo(kommune(12, 34), k35)
                    retireTo(kommune(12, 36), k35)
                },
            )
            val idFor = MemoIdFor()

            val creates = transformer.creates(idFor::invoke)

            creates.map { it.row() } shouldBe listOf(Row(idFor(TestIdType.Kommune, k35), k35, null, Info("35")))
            creates.single().sourceEntity.shouldBeNull()
            creates.single().sammenslaaing.shouldBeNull()
            transformer.rows(Entity(kommuneId(1234), kommune(12, 34)), idFor) shouldBe listOf(
                Row(kommuneId(1234), k35, null, null),
            )
            transformer.rows(Entity(kommuneId(1236), kommune(12, 36)), idFor) shouldBe listOf(
                Row(kommuneId(1236), k35, null, null),
            )
        }

        test("ingen Create gir ingen transformasjoner") {
            transformerOf(reform { retireTo(kommune(12, 34), kommune(12, 35)) }).creates(MemoIdFor()::invoke)
                .shouldBeEmpty()
        }

        test("Create uten IdType gir tydelig feil") {
            val target = veg(12, 35, 100)
            val transformer = transformerOf(reform { create(target, Info("veg")) })

            shouldThrow<IllegalStateException> { transformer.creates(MemoIdFor()::invoke) }
                .message.shouldContain("Ingen IdType for ${target.type}").shouldContain("Mangler IdTypeProvider?")
        }
    }

    context("Spesialtilfeller som ikke kan lages via DTO-en") {
        test("vegdeling på tvers av fylker [Avvik: Vegdeling på tvers av fylker]") {
            val kilde = veg(12, 34, 100)
            val transformer = transformerOf(
                reform {
                    move(kilde, veg(12, 35, 100))
                    derive(veg(13, 1, 100), from = kilde)
                    derive(veg(14, 2, 200), from = kilde)
                },
            )
            val idFor = MemoIdFor()

            transformer.rows(Entity(fooId(1), kilde), idFor) shouldContainExactlyInAnyOrder listOf(
                Row(fooId(1), veg(12, 35, 100), null, null),
                Row(idFor(TestIdType.Foo, veg(13, 1, 100)), veg(13, 1, 100), null, null),
                Row(idFor(TestIdType.Foo, veg(14, 2, 200)), veg(14, 2, 200), null, null),
            )
            transformer.rows(Entity(fooId(2), vegadresse(12, 34, 100, 1))) shouldBe listOf(
                Row(fooId(2), vegadresse(12, 35, 100, 1), null, null),
            )
        }

        test("DTO-en kan ikke uttrykke vegdeling på tvers av fylker") {
            val input = Reguleringsinput(
                "reg",
                dato,
                listOf(
                    Vegendring(
                        FraEnTilMange(fnr(12), listOf(fnr(12), fnr(13))),
                        FraEnTilMange(klnr(34), listOf(klnr(35), klnr(1))),
                        FraEnTilMange(Adressekode(100), listOf(Adressekode(100))),
                    ),
                ),
                emptyList(),
                emptyList(),
            )

            shouldThrow<IllegalArgumentException> { input.toReformOps() }
        }

        test("gårdsnummerserie: bruksnumre uten egen regel følger serien [Avvik: Gårdsnummerserie]") {
            val transformer = transformerOf(
                reform {
                    move(gnr(10, 20, 40), gnr(10, 90, 60))
                    move(grunneiendom(10, 20, 40, 1), grunneiendom(80, 99, 70, 1))
                },
            )

            transformer.rows(Entity(fooId(1), grunneiendom(10, 20, 40, 1))) shouldBe listOf(
                Row(fooId(1), grunneiendom(80, 99, 70, 1), null, null),
            )
            transformer.rows(Entity(fooId(2), grunneiendom(10, 20, 40, 2))) shouldBe listOf(
                Row(fooId(2), grunneiendom(10, 90, 60, 2), null, null),
            )
            val matrikkelenhet = transformer.rows(Entity(fooId(3), matrikkelenhet(10, 20, 40, 3))).single().ident!!
            matrikkelenhet shouldBe matrikkelenhet(10, 90, 60, 3)
            matrikkelenhet.type shouldBeSameInstanceAs getMatrikkelenhetIdentType()
            transformer.rows(Entity(fooId(4), matrikkelenhet(10, 20, 40, 1))) shouldBe listOf(
                Row(fooId(4), matrikkelenhet(80, 99, 70, 1), null, null),
            )
        }

        test("fylkeregel gjelder for kommuner uten egen regel [Avvik: Fylkeregler og kommunevise entiteter]") {
            val transformer = transformerOf(
                reform {
                    retire(fylke(30))
                    derive(fylke(31), from = fylke(30))
                    derive(fylke(32), from = fylke(30))
                },
            )

            transformer.rows(Entity(kommuneId(3001), kommune(30, 1))) shouldBe listOf(
                Row(kommuneId(3001), identTypeOf1<Kommunenummer.Lopenummer>()(klnr(1)), null, null),
            )
        }

        test("fylkedeling: fylket får null, ikke Ident.Empty [Avvik: Deling uten etterfølger]") {
            val transformer = transformerOf(
                reform {
                    retire(fylke(30))
                    derive(fylke(31), from = fylke(30), Info("31"))
                    derive(fylke(32), from = fylke(30), Info("32"))
                },
            )
            val idFor = MemoIdFor()

            transformer.rows(Entity(fylkeId(30), fylke(30)), idFor) shouldContainExactlyInAnyOrder listOf(
                Row(fylkeId(30), null, null, null),
                Row(idFor(TestIdType.Fylke, fylke(31)), fylke(31), null, Info("31")),
                Row(idFor(TestIdType.Fylke, fylke(32)), fylke(32), null, Info("32")),
            )
        }
    }

    // Port av `IdentTransformerTest`. `Mapping.Replace` med payload er `RetireTo` + `Derive`, `Mapping.Split` er
    // `Retire` + én `Derive` per del (kommune/fylke) eller `Move` + `Derive` (veg), og `Mapping.Simple` er `Move`.
    context("Port av IdentTransformerTest") {
        val k34 = kommune(12, 34)
        val k35 = kommune(12, 35)
        val k36 = kommune(12, 36)

        val flyttKommune = reform {
            retireTo(k34, k35)
            derive(k35, from = k34, Info("Ny kommune"))
        }
        val splittKommuneUtenKobling = reform {
            retire(k34)
            derive(k35, from = k34, Info("Ny kommune 1"))
            derive(k36, from = k34, Info("Ny kommune 2"))
        }
        val splittKommuneMedKobling = reform {
            retire(k34, successor = k35)
            derive(k35, from = k34, Info("Ny kommune 1"))
            derive(k36, from = k34, Info("Ny kommune 2"))
        }

        context("en kommune") {
            val entity = Entity(kommuneId(1234), k34)

            test("kommune flyttes: eksisterende kommune settes utgått og ny opprettes") {
                val idFor = MemoIdFor()
                transformerOf(flyttKommune).rows(entity, idFor) shouldContainExactlyInAnyOrder listOf(
                    Row(entity.id, k35, null, null),
                    Row(idFor(TestIdType.Kommune, k35), k35, null, Info("Ny kommune")),
                )
            }

            test("kommune splittes uten kobling: kilden får null [Avvik: Deling uten etterfølger]") {
                val idFor = MemoIdFor()
                transformerOf(splittKommuneUtenKobling).rows(entity, idFor) shouldContainExactlyInAnyOrder listOf(
                    Row(entity.id, null, null, null),
                    Row(idFor(TestIdType.Kommune, k35), k35, null, Info("Ny kommune 1")),
                    Row(idFor(TestIdType.Kommune, k36), k36, null, Info("Ny kommune 2")),
                )
            }

            test("kommune splittes med kobling [Avvik: Kommunedeling]") {
                val idFor = MemoIdFor()
                transformerOf(splittKommuneMedKobling).rows(entity, idFor) shouldContainExactlyInAnyOrder listOf(
                    Row(entity.id, k35, null, null),
                    Row(idFor(TestIdType.Kommune, k35), k35, null, Info("Ny kommune 1")),
                    Row(idFor(TestIdType.Kommune, k36), k36, null, Info("Ny kommune 2")),
                )
            }
        }

        context("bygning uten relasjoner") {
            val entity = Entity(fooId(1), bygning(12, 34, 123456))

            test("kommune flyttes: hovedident transformeres entydig") {
                transformerOf(flyttKommune).rows(entity) shouldBe listOf(
                    Row(entity.id, bygning(12, 35, 123456), null, null),
                )
            }

            test("splitt kommune: hovedident blir delvis nøkkel") {
                transformerOf(splittKommuneUtenKobling).rows(entity) shouldBe listOf(
                    Row(entity.id, identTypeOf1<Bygningsnummer>()(Bygningsnummer(123456)), null, null),
                )
            }
        }

        context("bygning med relasjoner") {
            val entity = Entity(fooId(1), bygning(12, 34, 123456), setOf(gnr(12, 34, 1), gnr(12, 34, 2)))

            test("kommune flyttes: alle identer transformeres entydig") {
                transformerOf(flyttKommune).rows(entity) shouldBe listOf(
                    Row(entity.id, bygning(12, 35, 123456), setOf(gnr(12, 35, 1), gnr(12, 35, 2)), null),
                )
            }

            test("splitt kommune og fordel gårdsnummer: hovedident delvis, assosierte entydige") {
                val ops = splittKommuneUtenKobling + reform {
                    move(gnr(12, 34, 1), gnr(12, 35, 1))
                    move(gnr(12, 34, 2), gnr(12, 36, 1))
                }

                transformerOf(ops).rows(entity) shouldBe listOf(
                    Row(
                        entity.id,
                        identTypeOf1<Bygningsnummer>()(Bygningsnummer(123456)),
                        setOf(gnr(12, 35, 1), gnr(12, 36, 1)),
                        null,
                    ),
                )
            }
        }

        val a34 = veg(12, 34, 10000)
        val flyttAdresseparsell = reform { move(a34, veg(12, 35, 20000)) }
        val splittAdresseparsell = reform {
            move(a34, veg(12, 35, 10000))
            derive(veg(12, 36, 10000), from = a34)
        }

        context("en adresseparsell") {
            val entity = Entity(fooId(1), a34)

            test("som flyttes hel") {
                transformerOf(flyttAdresseparsell).rows(entity) shouldBe listOf(
                    Row(entity.id, veg(12, 35, 20000), null, null),
                )
            }

            test("som splittes: adresseparsellen endres og en ny oppstår") {
                val idFor = MemoIdFor()
                transformerOf(splittAdresseparsell).rows(entity, idFor) shouldContainExactlyInAnyOrder listOf(
                    Row(entity.id, veg(12, 35, 10000), null, null),
                    Row(idFor(TestIdType.Foo, veg(12, 36, 10000)), veg(12, 36, 10000), null, null),
                )
            }
        }

        context("en vegadresse") {
            val entity = Entity(fooId(1), vegadresse(12, 34, 10000, 10))

            test("hvor adresseparsellen flyttes hel") {
                transformerOf(flyttAdresseparsell).rows(entity) shouldBe listOf(
                    Row(entity.id, vegadresse(12, 35, 20000, 10), null, null),
                )
            }

            test("hvor adresseparsellen splittes uten at adressen nevnes: følger første mål [Avvik: Vegdeling, vegadresser uten egen regel]") {
                transformerOf(splittAdresseparsell).rows(entity) shouldBe listOf(
                    Row(entity.id, vegadresse(12, 35, 10000, 10), null, null),
                )
            }

            test("hvor adresseparsellen splittes, men adressen har egen regel") {
                val ops = splittAdresseparsell + reform {
                    move(vegadresse(12, 34, 10000, 10), vegadresse(12, 36, 10000, 10))
                }
                transformerOf(ops).rows(entity) shouldBe listOf(
                    Row(entity.id, vegadresse(12, 36, 10000, 10), null, null),
                )
            }
        }
    }

    context("Port av IdentTransformerVegTest, IdentTransformerVegadresseTest og TransformEntityTest") {
        val kommuner = listOf(dummyKommune(3, 6), dummyKommune(3, 7), dummyKommune(5, 7), dummyKommune(5, 8))
        fun input(vararg endringer: Endring) =
            Reguleringsinput("123", dato, endringer.toList(), emptyList(), kommuner)

        val kommuneendring = Kommuneendring(
            FraEnTilMange(fnr(2), listOf(fnr(3))),
            FraEnTilMange(klnr(5), listOf(klnr(6), klnr(7))),
        )
        val vegendring2500 = Vegendring(
            FraEnTilMange(fnr(2), listOf(fnr(3))),
            FraEnTilMange(klnr(5), listOf(klnr(6))),
            FraEnTilMange(Adressekode(2500), listOf(Adressekode(2500))),
        )
        val vegendring2600 = Vegendring(
            FraEnTilMange(fnr(2), listOf(fnr(3))),
            FraEnTilMange(klnr(5), listOf(klnr(6), klnr(7))),
            FraEnTilMange(Adressekode(2600), listOf(Adressekode(2600))),
        )

        test("veg: én ident matcher") {
            val transformer = transformerOf(input(kommuneendring, vegendring2500, vegendring2600).toReformOps())

            transformer.rows(Entity(fooId(123), veg(2, 5, 2500))) shouldBe listOf(
                Row(fooId(123), veg(3, 6, 2500), null, null),
            )
        }

        test("veg: deling gir en ny veg per ekstra mål") {
            val transformer = transformerOf(input(kommuneendring, vegendring2500, vegendring2600).toReformOps())
            val idFor = MemoIdFor()

            transformer.rows(Entity(fooId(123), veg(2, 5, 2600)), idFor) shouldContainExactlyInAnyOrder listOf(
                Row(fooId(123), veg(3, 6, 2600), null, null),
                Row(idFor(TestIdType.Foo, veg(3, 7, 2600)), veg(3, 7, 2600), null, null),
            )
        }

        test("vegadresse: én ident matcher") {
            val transformer = transformerOf(
                input(
                    Vegadresseendring(
                        FraTil(fnr(2), fnr(3)),
                        FraTil(klnr(5), klnr(6)),
                        FraTil(Adressekode(2600), Adressekode(2600)),
                        FraTil(Adressenummernummer(10), Adressenummernummer(10)),
                    ),
                ).toReformOps(),
            )

            transformer.rows(Entity(fooId(123), vegadresse(2, 5, 2600, 10))) shouldBe listOf(
                Row(fooId(123), vegadresse(3, 6, 2600, 10), null, null),
            )
        }

        val entityInput = input(
            Kommuneendring(FraEnTilMange(fnr(2), listOf(fnr(3))), FraEnTilMange(klnr(5), listOf(klnr(6)))),
            Kommuneendring(FraEnTilMange(fnr(5), listOf(fnr(5))), FraEnTilMange(klnr(5), listOf(klnr(7), klnr(8)))),
            Vegendring(
                FraEnTilMange(fnr(2), listOf(fnr(3))),
                FraEnTilMange(klnr(5), listOf(klnr(6), klnr(7))),
                FraEnTilMange(Adressekode(2500), listOf(Adressekode(2500), Adressekode(2500))),
            ),
        )

        test("flytting av kommune gir én transformasjon for utgått og én for ny kommune") {
            val idFor = MemoIdFor()
            val k36 = kommune(3, 6)

            transformerOf(entityInput.toReformOps()).rows(Entity(kommuneId(205), kommune(2, 5)), idFor) shouldContainExactlyInAnyOrder
                listOf(
                    Row(kommuneId(205), k36, null, null),
                    Row(idFor(TestIdType.Kommune, k36), k36, null, dummyKommune(3, 6).tilKommunedata(dato)),
                )
        }

        test("splitting av kommune gir tre transformasjoner") {
            val result = transformerOf(entityInput.toReformOps()).run(Entity(kommuneId(505), kommune(5, 5)))

            result shouldHaveSize 3
            result.map { it.transformedIdent } shouldContainExactlyInAnyOrder listOf(
                kommune(5, 7),
                kommune(5, 7),
                kommune(5, 8),
            )
            result.single { it.id == kommuneId(505) }.resultObject.shouldBeNull()
        }

        test("ingen transformasjon når ingen identer matcher") {
            transformerOf(entityInput.toReformOps()).rows(Entity(kommuneId(1050), kommune(10, 50))).shouldBeEmpty()
        }

        test("assosierte identer: bare de med treff endres") {
            val transformer = transformerOf(entityInput.toReformOps())

            transformer.rows(Entity(fooId(123), associatedIdents = setOf(gnr(2, 5, 1), gnr(2, 5, 2)))) shouldBe
                listOf(Row(fooId(123), null, setOf(gnr(3, 6, 1), gnr(3, 6, 2)), null))
            transformer.rows(Entity(fooId(123), associatedIdents = setOf(gnr(2, 5, 1), gnr(10, 15, 1)))) shouldBe
                listOf(Row(fooId(123), null, setOf(gnr(3, 6, 1), gnr(10, 15, 1)), null))
            transformer.rows(Entity(fooId(123), associatedIdents = setOf(gnr(10, 15, 1), gnr(2, 5, 1)))) shouldBe
                listOf(Row(fooId(123), null, setOf(gnr(10, 15, 1), gnr(3, 6, 1)), null))
        }
    }

    context("Port av IdentTransformerBygningTest") {
        val ops = reform {
            retireTo(gnr(2, 5, 1), gnr(2, 6, 1))
            retire(kommune(2, 5), successor = kommune(2, 7))
            derive(kommune(2, 7), from = kommune(2, 5))
            derive(kommune(2, 8), from = kommune(2, 5))
        }

        test("bygning mister fylke og kommune, assosiert gårdsnummer følger egen regel") {
            transformerOf(ops).rows(Entity(fooId(123), bygning(2, 5, 123456789), setOf(gnr(2, 5, 1)))) shouldBe
                listOf(
                    Row(
                        fooId(123),
                        identTypeOf1<Bygningsnummer>()(Bygningsnummer(123456789)),
                        setOf(gnr(2, 6, 1)),
                        null,
                    ),
                )
        }

        test("identer uten endringer beholdes") {
            val entity = Entity(fooId(123), bygning(2, 5, 123456789), setOf(gnr(2, 5, 1), gnr(2, 10, 1)))

            transformerOf(ops).rows(entity) shouldBe listOf(
                Row(
                    fooId(123),
                    identTypeOf1<Bygningsnummer>()(Bygningsnummer(123456789)),
                    setOf(gnr(2, 6, 1), gnr(2, 10, 1)),
                    null,
                ),
            )
        }
    }

    context("Port av TransformerMatrikkelenhetTest") {
        val kommuner = listOf(dummyKommune(12, 35), dummyKommune(12, 36), dummyKommune(80, 99))
        fun input(vararg endringer: Endring) =
            Reguleringsinput("abc", dato, endringer.toList(), emptyList(), kommuner)

        val delKommune = Kommuneendring(
            FraEnTilMange(fnr(12), listOf(fnr(12))),
            FraEnTilMange(klnr(34), listOf(klnr(35), klnr(36))),
        )
        val unresolved = getUnresolvedMatrikkelenhetIdentType()

        test("del kommune, manglende regel for matrikkelenhet gir delvis nøkkel") {
            val transformer = transformerOf(
                input(
                    delKommune,
                    Matrikkelenhetendring(FraTil(fnr(12), fnr(12)), FraTil(klnr(34), klnr(35)), Gardsnummer(1), Gardsnummer(1)),
                ).toReformOps(),
            )

            transformer.rows(Entity(fooId(1), matrikkelenhet(12, 34, 1, 1))) shouldBe listOf(
                Row(fooId(1), matrikkelenhet(12, 35, 1, 1), null, null),
            )
            transformer.rows(Entity(fooId(2), matrikkelenhet(12, 34, 2, 1))) shouldBe listOf(
                Row(fooId(2), unresolved(Gardsnummer(2), Bruksnummer(1), Festenummer(0), Seksjonsnummer(0)), null, null),
            )
        }

        suspend fun g40(bnr: Short) = grunneiendom(10, 20, 40, bnr)
        suspend fun g50(bnr: Short) = grunneiendom(10, 30, 50, bnr)
        suspend fun g60(bnr: Short) = grunneiendom(10, 90, 60, bnr)
        suspend fun g70(bnr: Short) = grunneiendom(80, 99, 70, bnr)

        test("del matrikkelenhet i 2 nye kommuner") {
            val transformer = transformerOf(
                input(
                    Matrikkelenhetendring(
                        FraTil(fnr(10), fnr(10)), FraTil(klnr(20), klnr(90)), Gardsnummer(40), Gardsnummer(60),
                        mapOf(
                            Bruksnummer(1) to g70(1),
                            Bruksnummer(2) to g60(2),
                            Bruksnummer(3) to g60(3),
                        ),
                    ),
                ).toReformOps(),
            )

            (1..3).map { transformer.rows(Entity(fooId(it.toLong()), g40(it.toShort()))).single().ident } shouldBe
                listOf(g70(1), g60(2), g60(3))
        }

        test("del matrikkelenhet i 2 nye kommuner, ikke komplette parametere [Avvik: Gårdsnummerserie]") {
            val transformer = transformerOf(
                input(
                    Matrikkelenhetendring(
                        FraTil(fnr(10), fnr(10)), FraTil(klnr(20), klnr(90)), Gardsnummer(40), Gardsnummer(60),
                        mapOf(Bruksnummer(1) to g70(1)),
                    ),
                ).toReformOps(),
            )

            // I dag får bruksnummer 2 og 3 delvis nøkkel. Nå følger de serien til gårdsnummer 60.
            (1..3).map { transformer.rows(Entity(fooId(it.toLong()), g40(it.toShort()))).single().ident } shouldBe
                listOf(g70(1), g60(2), g60(3))
        }

        test("splitt grunneiendom, feil på bevaring av gårdsnummer") {
            val dto = input(
                Matrikkelenhetendring(
                    FraTil(fnr(10), fnr(10)), FraTil(klnr(20), klnr(20)), Gardsnummer(40), null,
                    mapOf(
                        Bruksnummer(1) to g70(1),
                        Bruksnummer(2) to g60(2),
                        Bruksnummer(3) to g40(3),
                    ),
                ),
            )

            shouldThrow<IllegalArgumentException> { dto.toReformOps() }
                .message.shouldContain("gårdsnummeret bevares for noen bruksnummer")
        }

        test("flytt bruksnummer, bevar gårdsnummer") {
            val transformer = transformerOf(
                input(
                    Matrikkelenhetendring(
                        FraTil(fnr(10), fnr(10)), FraTil(klnr(20), klnr(20)), Gardsnummer(40), Gardsnummer(40),
                        mapOf(Bruksnummer(1) to g50(4), Bruksnummer(2) to g50(5)),
                    ),
                ).toReformOps(),
            )

            (1..3).map { transformer.rows(Entity(fooId(it.toLong()), g40(it.toShort()))).singleOrNull()?.ident } shouldBe
                listOf(g50(4), g50(5), null)
        }
    }
})
