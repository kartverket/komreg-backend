package no.kartverket.komreg.transformation

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.kotest.assertions.failure
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.*
import java.util.concurrent.atomic.AtomicLong

class IdentTransformerTest : BehaviorSpec({
    val kommuneIdentType: KommuneIdentType = runBlocking { identTypeOf2() }
    val bygningIdentType: BygningIdentType = runBlocking { identTypeOf3() }
    val unresolvedBygningIdentType: UnresolvedBygningIdentType = runBlocking { identTypeOf1() }
    val gardsnummerIdentType: GardsnummerIdentType = runBlocking { identTypeOf3() }
    val adresseparsellIdentType: AdresseparsellIdentType = runBlocking { identTypeOf3() }
    val vegadresseIdentType: VegadresseIdentType = runBlocking { identTypeOf4() }
    val unresolvedVegadresseIdentType: UnresolvedVegadresseIdentType = runBlocking { identTypeOf1() }
    val nextId = AtomicLong(1)

    fun idProvider(idType: IdType<*, *>, @Suppress("UNUSED_PARAMETER") hint: Any?): Id {
        return when (idType) {
            is TestIdType -> Id(idType, nextId.getAndIncrement())
            else -> throw failure("Unknown id type $idType")
        }
    }

    fun flyttKommune() = IdentTransformer(
        kommuneIdentType(
            Fylkesnummer(12),
            Kommunenummer.Lopenummer(34)
        ) to IdentTransformer.Mapping.Replace(
            kommuneIdentType(
                Fylkesnummer(12),
                Kommunenummer.Lopenummer(35)
            ),
            MockKommuneinfo("Ny kommune")
        )
    )

    fun splittKommuneUtenKobling() = IdentTransformer(
        kommuneIdentType(
            Fylkesnummer(12),
            Kommunenummer.Lopenummer(34)
        ) to IdentTransformer.Mapping.Split(
            listOf(
                Ident.Empty to null,
                kommuneIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(35)
                ) to MockKommuneinfo("Ny kommune 1"),
                kommuneIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(36)
                ) to MockKommuneinfo("Ny kommune 2"),
            )
        )
    )

    fun splittKommuneMedKobling() = IdentTransformer(
        kommuneIdentType(
            Fylkesnummer(12),
            Kommunenummer.Lopenummer(34)
        ) to IdentTransformer.Mapping.Split(
            listOf(
                kommuneIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(35)
                ) to null,
                kommuneIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(35)
                ) to MockKommuneinfo("Ny kommune 1"),
                kommuneIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(36)
                ) to MockKommuneinfo("Ny kommune 2"),
            )
        )
    )

    Given("transformasjon med to regler") {
        val matrikkelenhetIdentType = getMatrikkelenhetIdentType()
        val idGeneratorManager = mockk<IdGeneratorManager>()

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
            )
        )


        val transformation = IdentTransformer(
            kommuneIdentType(fnr, klnr) to IdentTransformer.Mapping.Split(
                listOf(
                    kommuneIdentType(fnr, klnr) to null,
                    kommuneIdentType(fnr, Kommunenummer.Lopenummer(22)) to null,
                )
            ),
            gardsnummerIdentType(fnr, klnr, Matrikkelnummer.Gardsnummer(4)) to IdentTransformer.Mapping.Simple(
                gardsnummerIdentType(Fylkesnummer(43), Kommunenummer.Lopenummer(21), Matrikkelnummer.Gardsnummer(5))
            )
        )
            .transform(entity, idGeneratorManager::idFor)

        Then("lengste regel brukes") {
            assertThat(transformation?.singleOrNull()?.transformedIdent, "transformedIdent")
                .isEqualTo(
                    matrikkelenhetIdentType(
                        Fylkesnummer(43),
                        Kommunenummer.Lopenummer(21),
                        Matrikkelnummer.Gardsnummer(5),
                        Matrikkelnummer.Bruksnummer(3),
                        Matrikkelnummer.Festenummer(2),
                        Matrikkelnummer.Seksjonsnummer(1),
                    )
                )
        }
    }

    Given("en kommune") {
        val entity = Entity(
            id = idProvider(TestIdType.Foo, null),
            ident = kommuneIdentType(
                Fylkesnummer(12),
                Kommunenummer.Lopenummer(34),
            )
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
                                .isEqualTo(
                                    kommuneIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                        index(1).all {
                            prop(Transformation::id).isNotEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    kommuneIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
                            prop(Transformation::resultObject).isEqualTo(MockKommuneinfo("Ny kommune"))
                        }
                    }
            }
        }

        When("kommune splittes uten ny kommune-kobling") {
            val transformer = splittKommuneUtenKobling()

            Then("eksisterende kommune settes utgått og to nye opprettes") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(3)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(Ident.Empty)
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                        index(1).all {
                            prop(Transformation::id).isNotEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    kommuneIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
                            prop(Transformation::resultObject).isEqualTo(MockKommuneinfo("Ny kommune 1"))
                        }
                        index(2).all {
                            prop(Transformation::id).isNotEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    kommuneIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(36)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
                            prop(Transformation::resultObject).isEqualTo(MockKommuneinfo("Ny kommune 2"))
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
                                .isEqualTo(
                                    kommuneIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                        index(1).all {
                            prop(Transformation::id).isNotEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    kommuneIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(35)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
                            prop(Transformation::resultObject).isEqualTo(MockKommuneinfo("Ny kommune 1"))
                        }
                        index(2).all {
                            prop(Transformation::id).isNotEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    kommuneIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(36)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
                            prop(Transformation::resultObject).isEqualTo(MockKommuneinfo("Ny kommune 2"))
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
                Bygningsnummer(123456)
            )
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
                                        Bygningsnummer(123456)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }

        When("splitt kommune") {
            val transformer = splittKommuneUtenKobling()

            Then("transformasjon av hovedident vil være tvetydig") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(1)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    unresolvedBygningIdentType(
                                        Bygningsnummer(123456)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
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
                Bygningsnummer(123456)
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
            )
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
                                        Bygningsnummer(123456)
                                    )
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
            val transformer = IdentTransformer(
                kommuneIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34)
                ) to IdentTransformer.Mapping.Split(
                    listOf(
                        Ident.Empty to null,
                        kommuneIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(35)
                        ) to MockKommuneinfo("Ny kommune 1"),
                        kommuneIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(36)
                        ) to MockKommuneinfo("Ny kommune 2"),
                    )
                ),
                gardsnummerIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Matrikkelnummer.Gardsnummer(1)
                ) to IdentTransformer.Mapping.Simple(
                    gardsnummerIdentType(
                        Fylkesnummer(12),
                        Kommunenummer.Lopenummer(35),
                        Matrikkelnummer.Gardsnummer(1)
                    )
                ),
                gardsnummerIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Matrikkelnummer.Gardsnummer(2)
                ) to IdentTransformer.Mapping.Simple(
                    gardsnummerIdentType(
                        Fylkesnummer(12),
                        Kommunenummer.Lopenummer(36),
                        Matrikkelnummer.Gardsnummer(1) // Endrer denne også
                    )
                ),
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
                                .isEqualTo(
                                    unresolvedBygningIdentType(
                                        Bygningsnummer(123456)
                                    )
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

    fun flyttAdresseparsell() = IdentTransformer(
        adresseparsellIdentType(
            Fylkesnummer(12),
            Kommunenummer.Lopenummer(34),
            Adressekode(10000)
        ) to IdentTransformer.Mapping.Simple(
            adresseparsellIdentType(
                Fylkesnummer(12),
                Kommunenummer.Lopenummer(35),
                Adressekode(20000)
            )
        )
    )

    fun splittAdresseparsell() = IdentTransformer(
        adresseparsellIdentType(
            Fylkesnummer(12),
            Kommunenummer.Lopenummer(34),
            Adressekode(10000)
        ) to IdentTransformer.Mapping.Split(
            listOf(
                adresseparsellIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(35),
                    Adressekode(10000)
                ) to null,
                adresseparsellIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(36),
                    Adressekode(10000)
                ) to null,
            )
        )
    )

    Given("en adresseparsell") {
        val entity = Entity(
            id = idProvider(TestIdType.Foo, null),
            ident = adresseparsellIdentType(
                Fylkesnummer(12),
                Kommunenummer.Lopenummer(34),
                Adressekode(10000)
            )
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
                                        Adressekode(20000)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
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
                                        Adressekode(10000)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                        index(1).all {
                            prop(Transformation::id).isNotEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    adresseparsellIdentType(
                                        Fylkesnummer(12),
                                        Kommunenummer.Lopenummer(36),
                                        Adressekode(10000)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
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
                Adressenummernummer(10)
            )
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
                                        Adressenummernummer(10)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }

        When("hvor adresseparsellen splittes uten at adressen nevnes") {
            val transformer = splittAdresseparsell()

            Then("vegadressen transformeres tvetydig") {
                val result = transformer.transform(entity, ::idProvider)

                assertThat(result, "result")
                    .isNotNull()
                    .apply {
                        hasSize(1)
                        index(0).all {
                            prop(Transformation::id).isEqualTo(entity.id)
                            prop(Transformation::transformedIdent)
                                .isEqualTo(
                                    unresolvedVegadresseIdentType(
                                        Adressenummernummer(10)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }

        When("hvor adresseparsellen splittes, men adressen har egen regel") {
            val transformer = IdentTransformer(
                adresseparsellIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Adressekode(10000)
                ) to IdentTransformer.Mapping.Split(
                    listOf(
                        adresseparsellIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(35),
                            Adressekode(10000)
                        ) to null,
                        adresseparsellIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(36),
                            Adressekode(10000)
                        ) to null,
                    )
                ),
                vegadresseIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Adressekode(10000),
                    Adressenummernummer(10)
                ) to IdentTransformer.Mapping.Simple(
                    vegadresseIdentType(
                        Fylkesnummer(12),
                        Kommunenummer.Lopenummer(35),
                        Adressekode(10000),
                        Adressenummernummer(10)
                    )
                )
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
                                        Adressenummernummer(10)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }
    }
}) {
    private data class MockKommuneinfo(val kommunenavn: String) : Payload
}
