package no.kartverket.komreg.transformation

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import assertk.fail
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.*
import no.kartverket.komreg.parameter.compat.IdentTransformer
import no.kartverket.komreg.parameter.compat.Parameters
import no.kartverket.komreg.parameter.data.HList
import no.kartverket.komreg.parameter.data.times
import java.util.concurrent.atomic.AtomicLong
import no.kartverket.komreg.core.domain.Fylkesnummer as Fylke
import no.kartverket.komreg.core.domain.Kommunenummer.Lopenummer as Kommune
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer as Gard

class IdentTransformerTest : BehaviorSpec({
    val kommuneIdentType: KommuneIdentType = runBlocking { identTypeOf2() }
    val bygningIdentType: BygningIdentType = runBlocking { identTypeOf3() }
    val unresolvedBygningIdentType: UnresolvedBygningIdentType = runBlocking { identTypeOf1() }
    val gardsnummerIdentType: GardsnummerIdentType = runBlocking { identTypeOf3() }
    val adresseparsellIdentType: AdresseparsellIdentType = runBlocking { identTypeOf3() }
    val vegadresseIdentType: VegadresseIdentType = runBlocking { identTypeOf4() }
    val unresolvedVegadresseIdentType: UnresolvedVegadresseIdentType =
        runBlocking { identTypeOf1() }
    val nextId = AtomicLong(1)

    fun idProvider(idType: IdType<*, *>, @Suppress("UNUSED_PARAMETER") hint: Any?): Id {
        return when (idType) {
            is TestIdType -> Id(idType, nextId.getAndIncrement())
            else -> fail("Unknown id type $idType")
        }
    }

    fun flyttKommune(): IdentTransformer = Parameters {
        adjust(Fylke(12)) {
            create(
                `as` = Kommune(35),
                from = HList * Fylke(12) * Kommune(34),
                patchData = MockKommuneinfo("Ny kommune")
            )
        }
    }

    // TODO: Dette går ikke an med de nye parameterene, der opprettelesen av kommune
    //       ikke er en del av splitten, så det vil ikke opprettes noen kommuner
    //       da det ikke er noen ting som "trigger" det
//    fun splittKommuneUtenKobling() = Parameters {
//        adjust(Fylke(12)) {
//            split(Kommune(34)) {
//                to(HList * Fylke(12) * Kommune(35), MockKommuneinfo("Ny kommune 1"))
//                to(HList * Fylke(12) * Kommune(36), MockKommuneinfo("Ny kommune 2"))
//            }
//        }
//    }

    fun splittKommuneMedKobling() = Parameters {
        adjust(Fylke(12)) {
            split(Kommune(34)) {
                to(HList * Fylke(12) * Kommune(35), MockKommuneinfo("Ny kommune 1"))
                to(HList * Fylke(12) * Kommune(36), MockKommuneinfo("Ny kommune 2"))
                move(Gard(1), HList * Fylke(12) * Kommune(35) * Gard(1))
                move(Gard(2), HList * Fylke(12) * Kommune(36) * Gard(1))
            }
        }
    }

    Given("transformasjon med to regler") {
        val matrikkelenhetIdentType = getMatrikkelenhetIdentType()
        val idGeneratorManager = mockk<IdGeneratorManager>()

        val fnr = Fylke(12)
        val klnr = Kommune(34)

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

//        val transformation = IdentTransformerImpl(
//            kommuneIdentType(fnr, klnr) to IdentTransformerImpl.Mapping.Split(
//                listOf(
//                    kommuneIdentType(fnr, klnr) to null,
//                    kommuneIdentType(fnr, Kommune(22)) to null,
//                )
//            ),
//            gardsnummerIdentType(
//                fnr,
//                klnr,
//                Matrikkelnummer.Gardsnummer(4)
//            ) to IdentTransformerImpl.Mapping.Simple(
//                gardsnummerIdentType(Fylke(43), Kommune(21), Matrikkelnummer.Gardsnummer(5))
//            )
//        )
//            .transform(entity, idGeneratorManager::idFor)
        val transformation = Parameters{
            adjust(fnr) {
                split(klnr) {
                    move(Gard(1), HList * Fylke(43) * Kommune(22) * Gard(5))
                    move(Gard(4), HList * Fylke(43) * Kommune(21) * Gard(5))
                }
            }
        }.transform(entity, idGeneratorManager::idFor)

        Then("lengste regel brukes") {
            assertThat(transformation?.singleOrNull()?.transformedIdent, "transformedIdent")
                .isEqualTo(
                    matrikkelenhetIdentType(
                        Fylke(43),
                        Kommune(21),
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
                Fylke(12),
                Kommune(34),
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
                                        Fylke(12),
                                        Kommune(35)
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
                                        Fylke(12),
                                        Kommune(35)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNull()
                            prop(Transformation::resultObject).isEqualTo(MockKommuneinfo("Ny kommune"))
                        }
                    }
            }
        }

//        When("kommune splittes uten ny kommune-kobling") {
//            val transformer = splittKommuneUtenKobling()
//
//            Then("eksisterende kommune settes utgått og to nye opprettes") {
//                val result = transformer.transform(entity, ::idProvider)
//
//                assertThat(result, "result")
//                    .isNotNull()
//                    .apply {
//                        hasSize(3)
//                        index(0).all {
//                            prop(Transformation::id).isEqualTo(entity.id)
//                            prop(Transformation::transformedIdent)
//                                .isEqualTo(Ident.Empty)
//                            prop(Transformation::transformedAssociatedIdents)
//                                .isNull()
//                            prop(Transformation::resultObject).isNull()
//                        }
//                        index(1).all {
//                            prop(Transformation::id).isNotEqualTo(entity.id)
//                            prop(Transformation::transformedIdent)
//                                .isEqualTo(
//                                    kommuneIdentType(
//                                        Fylke(12),
//                                        Kommune(35)
//                                    )
//                                )
//                            prop(Transformation::transformedAssociatedIdents)
//                                .isNull()
//                            prop(Transformation::resultObject).isEqualTo(MockKommuneinfo("Ny kommune 1"))
//                        }
//                        index(2).all {
//                            prop(Transformation::id).isNotEqualTo(entity.id)
//                            prop(Transformation::transformedIdent)
//                                .isEqualTo(
//                                    kommuneIdentType(
//                                        Fylke(12),
//                                        Kommune(36)
//                                    )
//                                )
//                            prop(Transformation::transformedAssociatedIdents)
//                                .isNull()
//                            prop(Transformation::resultObject).isEqualTo(MockKommuneinfo("Ny kommune 2"))
//                        }
//                    }
//            }
//        }

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
                                        Fylke(12),
                                        Kommune(35)
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
                                        Fylke(12),
                                        Kommune(35)
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
                                        Fylke(12),
                                        Kommune(36)
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
                Fylke(12),
                Kommune(34),
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
                                        Fylke(12),
                                        Kommune(35),
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

//        When("splitt kommune") {
//            val transformer = splittKommuneUtenKobling()
//
//            Then("transformasjon av hovedident vil være tvetydig") {
//                val result = transformer.transform(entity, ::idProvider)
//
//                assertThat(result, "result")
//                    .isNotNull()
//                    .apply {
//                        hasSize(1)
//                        index(0).all {
//                            prop(Transformation::id).isEqualTo(entity.id)
//                            prop(Transformation::transformedIdent)
//                                .isEqualTo(
//                                    unresolvedBygningIdentType(
//                                        Bygningsnummer(123456)
//                                    )
//                                )
//                            prop(Transformation::transformedAssociatedIdents)
//                                .isNull()
//                            prop(Transformation::resultObject).isNull()
//                        }
//                    }
//            }
//        }
    }

    Given("bygning med relasjoner") {
        val entity = Entity(
            id = idProvider(TestIdType.Foo, null),
            ident = bygningIdentType(
                Fylke(12),
                Kommune(34),
                Bygningsnummer(123456)
            ),
            associatedIdents = setOf(
                gardsnummerIdentType(
                    Fylke(12),
                    Kommune(34),
                    Matrikkelnummer.Gardsnummer(1),
                ),
                gardsnummerIdentType(
                    Fylke(12),
                    Kommune(34),
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
                                        Fylke(12),
                                        Kommune(35),
                                        Bygningsnummer(123456)
                                    )
                                )
                            prop(Transformation::transformedAssociatedIdents)
                                .isNotNull()
                                .containsOnly(
                                    gardsnummerIdentType(
                                        Fylke(12),
                                        Kommune(35),
                                        Matrikkelnummer.Gardsnummer(1),
                                    ),
                                    gardsnummerIdentType(
                                        Fylke(12),
                                        Kommune(35),
                                        Matrikkelnummer.Gardsnummer(2),
                                    ),
                                )
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }

        When("splitt kommune og fordel gårdsnummer") {
//            val transformer = IdentTransformerImpl(
//                kommuneIdentType(
//                    Fylke(12),
//                    Kommune(34)
//                ) to IdentTransformerImpl.Mapping.Split(
//                    listOf(
//                        Ident.Empty to null,
//                        kommuneIdentType(
//                            Fylke(12),
//                            Kommune(35)
//                        ) to MockKommuneinfo("Ny kommune 1"),
//                        kommuneIdentType(
//                            Fylke(12),
//                            Kommune(36)
//                        ) to MockKommuneinfo("Ny kommune 2"),
//                    )
//                ),
//                gardsnummerIdentType(
//                    Fylke(12),
//                    Kommune(34),
//                    Matrikkelnummer.Gardsnummer(1)
//                ) to IdentTransformerImpl.Mapping.Simple(
//                    gardsnummerIdentType(
//                        Fylke(12),
//                        Kommune(35),
//                        Matrikkelnummer.Gardsnummer(1)
//                    )
//                ),
//                gardsnummerIdentType(
//                    Fylke(12),
//                    Kommune(34),
//                    Matrikkelnummer.Gardsnummer(2)
//                ) to IdentTransformerImpl.Mapping.Simple(
//                    gardsnummerIdentType(
//                        Fylke(12),
//                        Kommune(36),
//                        Matrikkelnummer.Gardsnummer(1) // Endrer denne også
//                    )
//                ),
//            )
            val transformer = Parameters {
                adjust(Fylke(12)) {
                    split(Kommune(34)) {
                        to(HList * Fylke(12) * Kommune(35))
                        to(HList * Fylke(12) * Kommune(36))
                        move(Gard(1), HList * Fylke(12) * Kommune(35) * Gard(1))
                        move(Gard(2), HList * Fylke(12) * Kommune(36) * Gard(1))
                    }
                }
            }

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
                                        Fylke(12),
                                        Kommune(35),
                                        Matrikkelnummer.Gardsnummer(1),
                                    ),
                                    gardsnummerIdentType(
                                        Fylke(12),
                                        Kommune(36),
                                        Matrikkelnummer.Gardsnummer(1),
                                    ),
                                )
                            prop(Transformation::resultObject).isNull()
                        }
                    }
            }
        }
    }

    fun flyttAdresseparsell() = IdentTransformerImpl(
        adresseparsellIdentType(
            Fylke(12),
            Kommune(34),
            Adressekode(10000)
        ) to IdentTransformerImpl.Mapping.Simple(
            adresseparsellIdentType(
                Fylke(12),
                Kommune(35),
                Adressekode(20000)
            )
        )
    )

    fun splittAdresseparsell() = IdentTransformerImpl(
        adresseparsellIdentType(
            Fylke(12),
            Kommune(34),
            Adressekode(10000)
        ) to IdentTransformerImpl.Mapping.Split(
            listOf(
                adresseparsellIdentType(
                    Fylke(12),
                    Kommune(35),
                    Adressekode(10000)
                ) to null,
                adresseparsellIdentType(
                    Fylke(12),
                    Kommune(36),
                    Adressekode(10000)
                ) to null,
            )
        )
    )

    Given("en adresseparsell") {
        val entity = Entity(
            id = idProvider(TestIdType.Foo, null),
            ident = adresseparsellIdentType(
                Fylke(12),
                Kommune(34),
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
                                        Fylke(12),
                                        Kommune(35),
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
                                        Fylke(12),
                                        Kommune(35),
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
                                        Fylke(12),
                                        Kommune(36),
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
                Fylke(12),
                Kommune(34),
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
                                        Fylke(12),
                                        Kommune(35),
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
            val transformer = IdentTransformerImpl(
                adresseparsellIdentType(
                    Fylke(12),
                    Kommune(34),
                    Adressekode(10000)
                ) to IdentTransformerImpl.Mapping.Split(
                    listOf(
                        adresseparsellIdentType(
                            Fylke(12),
                            Kommune(35),
                            Adressekode(10000)
                        ) to null,
                        adresseparsellIdentType(
                            Fylke(12),
                            Kommune(36),
                            Adressekode(10000)
                        ) to null,
                    )
                ),
                vegadresseIdentType(
                    Fylke(12),
                    Kommune(34),
                    Adressekode(10000),
                    Adressenummernummer(10)
                ) to IdentTransformerImpl.Mapping.Simple(
                    vegadresseIdentType(
                        Fylke(12),
                        Kommune(35),
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
                                        Fylke(12),
                                        Kommune(35),
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
    object MockKommuneinfo {
        operator fun invoke(kommunenavn: String): Kommunedata {
            return Kommunedata(
                navn = kommunenavn,
                koordinatsystem = Koordinatsystem.UTM32,
                senterpunkt = Koordinat(500_000.0, 0.0),
                nedsattKonsesjonsgrense = false,
                godkjenteGardsnumre = "",
                adresse = null, standardRekvirentOrgnummer = null,
                ikrafttredelsesdato = LocalDate(2020, 1, 1),
                kommunevapen = null
            )
        }
    }
}
