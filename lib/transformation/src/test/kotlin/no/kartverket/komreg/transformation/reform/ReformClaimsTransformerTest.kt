package no.kartverket.komreg.transformation.reform

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import io.kotest.assertions.failure
import io.kotest.core.spec.style.BehaviorSpec
import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Bygningsnummer
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.integration.spi.identTypeOf1
import no.kartverket.komreg.integration.spi.identTypeOf2
import no.kartverket.komreg.integration.spi.identTypeOf3
import no.kartverket.komreg.integration.spi.identTypeOf4
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.transformation.AdresseparsellIdentType
import no.kartverket.komreg.transformation.BygningIdentType
import no.kartverket.komreg.transformation.GardsnummerIdentType
import no.kartverket.komreg.transformation.KommuneIdentType
import no.kartverket.komreg.transformation.TestIdType
import no.kartverket.komreg.transformation.UnresolvedBygningIdentType
import no.kartverket.komreg.transformation.VegadresseIdentType
import no.kartverket.komreg.transformation.getMatrikkelenhetIdentType
import java.util.concurrent.atomic.AtomicLong

/**
 * Reform-ekvivalent til `LegacyIdentTransformerTest`. Se avvikene A1-A11 i `ReformClaimsTransformerTestSupport.kt`.
 * Avvikene som slår ut i enkelttester, er nevnt ved testen.
 *
 * | legacy                                              | reform                                                           |
 * |-----------------------------------------------------|------------------------------------------------------------------|
 * | `flyttKommune` (`Replace`)                          | `merge` med én kilde og `into.new` (A5)                          |
 * | `splittKommuneUtenKobling` (`Ident.Empty` først)    | fylke: `split` uten `role` (A2). Bygning: `splittKommuneMedKobling` |
 * | `splittKommuneMedKobling`                           | `split` med `role: successor` på første del (A6)                 |
 * | `flyttAdresseparsell` (`Simple`)                    | `move` av `addressCode` (A5)                                     |
 * | `splittAdresseparsell`                              | `split` av `addressCode` med `role: identity` på første del (A6) |
 * | kommune 1234 beholdes, og 1222 er ny (`Split`)      | `create` med `derivedFrom` (A7)                                  |
 * | «vegadressen transformeres tvetydig»                | ikke portert, erstattet av «vegadressen følger med» (A8)         |
 */
class ReformClaimsTransformerTest : BehaviorSpec({
    val kommuneIdentType: KommuneIdentType = runBlocking { identTypeOf2() }
    val bygningIdentType: BygningIdentType = runBlocking { identTypeOf3() }
    val unresolvedBygningIdentType: UnresolvedBygningIdentType = runBlocking { identTypeOf1() }
    val gardsnummerIdentType: GardsnummerIdentType = runBlocking { identTypeOf3() }
    val adresseparsellIdentType: AdresseparsellIdentType = runBlocking { identTypeOf3() }
    val vegadresseIdentType: VegadresseIdentType = runBlocking { identTypeOf4() }
    val fylkeIdentType = runBlocking { identTypeOf1<Fylkesnummer>() }
    val nextId = AtomicLong(1)

    fun idProvider(idType: IdType<*, *>, @Suppress("UNUSED_PARAMETER") hint: Any?): Id {
        return when (idType) {
            is TestIdType -> Id(idType, nextId.getAndIncrement())
            else -> throw failure("Unknown id type $idType")
        }
    }

    fun flyttKommune() = transformerFromSpec(
        """{ "op": "merge", "kind": "municipality", "from": [ ["12", "34"] ],
             "into": { "new": ["12", "35"], "payload": ${kommunePayload("Ny kommune")} } }""",
    )

    /** A2: uten `role` utgår fylket uten etterfølger. For kommuner avvises det (`RetireNeedsSuccessor`). */
    fun splittFylkeUtenKobling() = transformerFromSpec(
        """{ "op": "split", "kind": "county", "key": "12",
             "into": [ { "new": ["13"], "payload": { "navn": "Nytt fylke 1" } },
                       { "new": ["14"], "payload": { "navn": "Nytt fylke 2" } } ] }""",
    )

    /** A6: første del er etterfølger. */
    fun splittKommuneMedKobling() = transformerFromSpec(
        """{ "op": "split", "kind": "municipality", "key": ["12", "34"],
             "into": [ { "new": ["12", "35"], "role": "successor", "payload": ${kommunePayload("Ny kommune 1")} },
                       { "new": ["12", "36"], "payload": ${kommunePayload("Ny kommune 2")} } ] }""",
    )

    Given("transformasjon med to regler") {
        val matrikkelenhetIdentType = getMatrikkelenhetIdentType()

        val fnr = Fylkesnummer(12)
        val klnr = Kommunenummer.Lopenummer(34)

        val entity = Entity(
            id = Id(TestIdType.Foo, 1L),
            ident = matrikkelenhetIdentType(
                fnr,
                klnr,
                Matrikkelnummer.Gardsnummer(4),
                Matrikkelnummer.Bruksnummer(3),
                Matrikkelnummer.Festenummer(2),
                Matrikkelnummer.Seksjonsnummer(1),
            ),
        )

        // A7: kommune 1234 fortsetter, og 1222 er ny. Legacy skrev det som Split til seg selv og 1222.
        val transformation = transformerFromSpec(
            """{ "op": "create", "kind": "municipality", "key": ["12", "22"], "derivedFrom": ["12", "34"],
                 "payload": ${kommunePayload("Ny kommune")} }""",
            """{ "op": "adjust", "kind": "municipality", "key": ["12", "34"], "then": [
                 { "op": "move", "kind": "landNumber", "key": 4, "to": ["43", "21", 5] } ] }""",
        ).transform(entity, ::idProvider)

        Then("lengste regel brukes") {
            assertThat(transformation.single().transformedIdent, "transformedIdent")
                .isEqualTo(
                    matrikkelenhetIdentType(
                        Fylkesnummer(43),
                        Kommunenummer.Lopenummer(21),
                        Matrikkelnummer.Gardsnummer(5),
                        Matrikkelnummer.Bruksnummer(3),
                        Matrikkelnummer.Festenummer(2),
                        Matrikkelnummer.Seksjonsnummer(1),
                    ),
                )
        }
    }

    Given("en kommune") {
        val entity = Entity(
            id = idProvider(TestIdType.Foo, null),
            ident = kommuneIdentType(
                Fylkesnummer(12),
                Kommunenummer.Lopenummer(34),
            ),
        )

        When("kommune flyttes") {
            val transformer = flyttKommune()

            Then("eksisterende kommune settes utgått og ny opprettes") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(2)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(kommuneIdentType(Fylkesnummer(12), Kommunenummer.Lopenummer(35)))
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                        index(1).all {
                            prop(Transformation::id).isNotEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(kommuneIdentType(Fylkesnummer(12), Kommunenummer.Lopenummer(35)))
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isEqualTo(forventetKommunedata("Ny kommune"))
                        }
                    }
            }
        }

        When("kommune splittes med ny kommune-kobling") {
            val transformer = splittKommuneMedKobling()

            Then("eksisterende kommune settes utgått og to nye opprettes") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(3)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(kommuneIdentType(Fylkesnummer(12), Kommunenummer.Lopenummer(35)))
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                        index(1).all {
                            prop(Transformation::id).isNotEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(kommuneIdentType(Fylkesnummer(12), Kommunenummer.Lopenummer(35)))
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isEqualTo(forventetKommunedata("Ny kommune 1"))
                        }
                        index(2).all {
                            prop(Transformation::id).isNotEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(kommuneIdentType(Fylkesnummer(12), Kommunenummer.Lopenummer(36)))
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isEqualTo(forventetKommunedata("Ny kommune 2"))
                        }
                    }
            }
        }
    }

    // A2: legacy testet «kommune splittes uten ny kommune-kobling». For kommuner avviser domenereglene det
    // (`RetireNeedsSuccessor`), så samme oppførsel testes med et fylke, der det er gyldig.
    Given("et fylke") {
        val entity = Entity(
            id = idProvider(TestIdType.Foo, null),
            ident = fylkeIdentType(Fylkesnummer(12)),
        )

        When("fylket splittes uten etterfølger") {
            val transformer = splittFylkeUtenKobling()

            Then("eksisterende fylke utgår og to nye opprettes") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(3)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            // A2: legacy ga Ident.Empty
                            prop(Transformation::transformedIdent).isNull()
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                        index(1).all {
                            prop(Transformation::id).isNotEqualTo(entity.id)
                            prop(Transformation::transformedIdent).isEqualTo(fylkeIdentType(Fylkesnummer(13)))
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isEqualTo(Fylkesdata("Nytt fylke 1"))
                        }
                        index(2).all {
                            prop(Transformation::id).isNotEqualTo(entity.id)
                            prop(Transformation::transformedIdent).isEqualTo(fylkeIdentType(Fylkesnummer(14)))
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isEqualTo(Fylkesdata("Nytt fylke 2"))
                        }
                    }
            }
        }
    }

    Given("bygning uten relasjoner") {
        val entity = Entity(
            id = idProvider(TestIdType.Foo, null),
            ident = bygningIdentType(
                Fylkesnummer(12),
                Kommunenummer.Lopenummer(34),
                Bygningsnummer(123456),
            ),
        )

        When("kommune flyttes") {
            val transformer = flyttKommune()

            Then("hovedident skal transformeres entydig") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(1)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    bygningIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35),
                                        Bygningsnummer(123456),
                                    ),
                                )
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }

        When("splitt kommune") {
            // A2 og A8: legacy brukte `splittKommuneUtenKobling`. Kommunen må ha etterfølger, men bygningen får
            // likevel delvis nøkkel, fordi kommunen utgår (`Retire`) og bygningen ikke har egen regel.
            val transformer = splittKommuneMedKobling()

            Then("transformasjon av hovedident vil være tvetydig") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(1)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(unresolvedBygningIdentType(Bygningsnummer(123456)))
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }
    }

    Given("bygning med relasjoner") {
        val entity = Entity(
            id = idProvider(TestIdType.Foo, null),
            ident = bygningIdentType(
                Fylkesnummer(12),
                Kommunenummer.Lopenummer(34),
                Bygningsnummer(123456),
            ),
            associatedIdents = setOf(
                gardsnummerIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Matrikkelnummer.Gardsnummer(1),
                ),
                gardsnummerIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Matrikkelnummer.Gardsnummer(2),
                ),
            ),
        )

        When("kommune flyttes") {
            val transformer = flyttKommune()

            Then("alle identer skal transformeres entydig") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(1)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    bygningIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35),
                                        Bygningsnummer(123456),
                                    ),
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNotNull()
                                .containsOnly(
                                    gardsnummerIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35),
                                        Matrikkelnummer.Gardsnummer(1),
                                    ),
                                    gardsnummerIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35),
                                        Matrikkelnummer.Gardsnummer(2),
                                    ),
                                )
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }

        When("splitt kommune og fordel gårdsnummer") {
            // A2: kommunen må ha etterfølger. Bygningen får likevel delvis nøkkel (A8).
            val transformer = transformerFromSpec(
                """{ "op": "split", "kind": "municipality", "key": ["12", "34"],
                     "into": [ { "new": ["12", "35"], "role": "successor", "payload": ${kommunePayload("Ny kommune 1")} },
                               { "new": ["12", "36"], "payload": ${kommunePayload("Ny kommune 2")} } ],
                     "then": [
                       { "op": "move", "kind": "landNumber", "key": 1, "to": ["12", "35", 1] },
                       { "op": "move", "kind": "landNumber", "key": 2, "to": ["12", "36", 1] } ] }""",
            )

            Then("transformasjon av hovedident vil være tvetydig, men andre vil være entydig") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(1)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(unresolvedBygningIdentType(Bygningsnummer(123456)))
                            prop(Transformation::transformedAssociatedIdents)
                                .isNotNull()
                                .containsOnly(
                                    gardsnummerIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35),
                                        Matrikkelnummer.Gardsnummer(1),
                                    ),
                                    gardsnummerIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(36),
                                        Matrikkelnummer.Gardsnummer(1),
                                    ),
                                )
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }
    }

    fun flyttAdresseparsell() = transformerFromSpec(
        """{ "op": "move", "kind": "addressCode", "key": ["12", "34", 10000], "to": ["12", "35", 20000] }""",
    )

    /** A6: første del har `role: identity`, så den beholder entitetens ID. Legacy: første mål beholdt ID-en. */
    fun splittAdresseparsell() = transformerFromSpec(
        """{ "op": "split", "kind": "addressCode", "key": ["12", "34", 10000],
             "into": [ { "new": ["12", "35", 10000], "role": "identity" }, { "new": ["12", "36", 10000] } ] }""",
    )

    Given("en adresseparsell") {
        val entity = Entity(
            id = idProvider(TestIdType.Foo, null),
            ident = adresseparsellIdentType(
                Fylkesnummer(12),
                Kommunenummer.Lopenummer(34),
                Adressekode(10000),
            ),
        )

        When("som flyttes hel") {
            val transformer = flyttAdresseparsell()

            Then("adresseparsellen endres") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(1)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    adresseparsellIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35),
                                        Adressekode(20000),
                                    ),
                                )
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }

        When("som splittes") {
            val transformer = splittAdresseparsell()

            Then("adresseparsellen endres og en ny oppstår") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(2)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    adresseparsellIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35),
                                        Adressekode(10000),
                                    ),
                                )
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                        index(1).all {
                            prop(Transformation::id).isNotEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    adresseparsellIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(36),
                                        Adressekode(10000),
                                    ),
                                )
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }
    }

    Given("en vegadresse") {
        val entity = Entity(
            id = idProvider(TestIdType.Foo, null),
            ident = vegadresseIdentType(
                Fylkesnummer(12),
                Kommunenummer.Lopenummer(34),
                Adressekode(10000),
                Adressenummernummer(10),
            ),
        )

        When("hvor adresseparsellen flyttes hel") {
            val transformer = flyttAdresseparsell()

            Then("vegadressen endres") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(1)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    vegadresseIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35),
                                        Adressekode(20000),
                                        Adressenummernummer(10),
                                    ),
                                )
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }

        When("hvor adresseparsellen splittes med identitetsdel uten at adressen nevnes") {
            // A8: erstatter legacy-testen «vegadressen transformeres tvetydig». Med `role: identity` er
            // adresseparsellen flyttet (`Move`), så vegadressen følger med til identitetsdelen. Legacy ga delvis
            // nøkkel, som vegadresse-sinken ikke støtter.
            val transformer = splittAdresseparsell()

            Then("vegadressen følger med til identitetsdelen") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(1)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    vegadresseIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35),
                                        Adressekode(10000),
                                        Adressenummernummer(10),
                                    ),
                                )
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }

        When("hvor adresseparsellen splittes, men adressen har egen regel") {
            val transformer = transformerFromSpec(
                """{ "op": "split", "kind": "addressCode", "key": ["12", "34", 10000],
                     "into": [ { "new": ["12", "35", 10000], "role": "identity" }, { "new": ["12", "36", 10000] } ],
                     "then": [ { "op": "move", "kind": "addressNumber", "key": 10, "to": ["12", "35", 10000, 10] } ] }""",
            )

            Then("vegadressen transformeres entydig") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(1)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    vegadresseIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35),
                                        Adressekode(10000),
                                        Adressenummernummer(10),
                                    ),
                                )
                            prop(Transformation::transformedAssociatedIdents).isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }
    }
})
