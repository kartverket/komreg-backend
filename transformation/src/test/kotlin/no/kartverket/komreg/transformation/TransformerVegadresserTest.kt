package no.kartverket.komreg.transformation

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.integration.spi.invoke

class TransformerVegadresserTest : FunSpec({
    // Faktiske id-typer er irrelevant
    fun vegId(idValue: Long) = Id(TestIdType.Foo, idValue)

    fun vegadresseId(idValue: Long) = Id(TestIdType.Foo, idValue)

    test("Ignorer uinvolverte") {
        val idGeneratorManager = mockIdGenerator()

        val adresseparsellIdentType = getAdresseparsellIdentType()

        val vegSource =
            mockSource(
                Entity(
                    id = vegId(1),
                    ident =
                        adresseparsellIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(34),
                            Adressekode(10100),
                        ),
                ),
                Entity(
                    id = vegId(2),
                    ident =
                        adresseparsellIdentType(
                            Fylkesnummer(56),
                            Kommunenummer.Lopenummer(78),
                            Adressekode(10100),
                        ),
                ),
            )

        val sink = MockSink()

        transform(
            1,
            Reguleringsinput(
                "abc",
                Clock.System.todayIn(TimeZone.currentSystemDefault()),
                listOf(
                    Vegendring(
                        FraEnTilMange(Fylkesnummer(12), listOf(Fylkesnummer(12))),
                        FraEnTilMange(Kommunenummer.Lopenummer(34), listOf(Kommunenummer.Lopenummer(35))),
                        FraEnTilMange(Adressekode(10100), listOf(Adressekode(20100))),
                    ),
                ),
                emptyList(),
                emptyList(),
            ),
            emptyList(),
            listOf(vegSource),
            emptyList(),
            listOf(sink),
            idGeneratorManager,
            TestStorage(),
            true,
        )

        assertThat(sink::transformations).single().prop(Transformation::id).isEqualTo(vegId(1))
    }

    test("Flytt hel adresseparsell") {
        val idGeneratorManager = mockIdGenerator()

        val adresseparsellIdentType = getAdresseparsellIdentType()
        val vegadresseIdentType = getVegadresseIdentType()

        val vegSource =
            mockSource(
                Entity(
                    id = vegId(1),
                    ident =
                        adresseparsellIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(34),
                            Adressekode(10100),
                        ),
                ),
            )
        val vegadresseSource =
            mockSource(
                Entity(
                    id = vegadresseId(10),
                    ident =
                        vegadresseIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(34),
                            Adressekode(10100),
                            Adressenummernummer(1),
                        ),
                ),
                Entity(
                    id = vegadresseId(11),
                    ident =
                        vegadresseIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(34),
                            Adressekode(10100),
                            Adressenummernummer(2),
                        ),
                ),
            )

        val sink = MockSink()

        transform(
            1,
            Reguleringsinput(
                "abc",
                Clock.System.todayIn(TimeZone.currentSystemDefault()),
                listOf(
                    Vegendring(
                        FraEnTilMange(Fylkesnummer(12), listOf(Fylkesnummer(12))),
                        FraEnTilMange(Kommunenummer.Lopenummer(34), listOf(Kommunenummer.Lopenummer(35))),
                        FraEnTilMange(Adressekode(10100), listOf(Adressekode(20100))),
                    ),
                ),
                emptyList(),
                emptyList(),
            ),
            emptyList(),
            listOf(vegSource, vegadresseSource),
            emptyList(),
            listOf(sink),
            idGeneratorManager,
            TestStorage(),
            true,
        )

        assertThat(sink::transformations).all {
            hasSize(3)
            index(0).all {
                prop(Transformation::id).isEqualTo(vegId(1))
                prop(Transformation::transformedIdent).isEqualTo(
                    adresseparsellIdentType(
                        Fylkesnummer(12),
                        Kommunenummer.Lopenummer(35),
                        Adressekode(20100),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
            index(1).all {
                prop(Transformation::id).isEqualTo(vegadresseId(10))
                prop(Transformation::transformedIdent).isEqualTo(
                    vegadresseIdentType(
                        Fylkesnummer(12),
                        Kommunenummer.Lopenummer(35),
                        Adressekode(20100),
                        Adressenummernummer(1),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
            index(2).all {
                prop(Transformation::id).isEqualTo(vegadresseId(11))
                prop(Transformation::transformedIdent).isEqualTo(
                    vegadresseIdentType(
                        Fylkesnummer(12),
                        Kommunenummer.Lopenummer(35),
                        Adressekode(20100),
                        Adressenummernummer(2),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
        }
    }

    context("Splitt adresseparsell") {
        val adresseparsellIdentType = getAdresseparsellIdentType()
        val vegadresseIdentType = getVegadresseIdentType()

        val vegSource =
            mockSource(
                Entity(
                    id = vegId(1),
                    ident =
                        adresseparsellIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(34),
                            Adressekode(10100),
                        ),
                ),
            )
        val vegadresseSource =
            mockSource(
                Entity(
                    id = vegadresseId(10),
                    ident =
                        vegadresseIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(34),
                            Adressekode(10100),
                            Adressenummernummer(1),
                        ),
                ),
                Entity(
                    id = vegadresseId(11),
                    ident =
                        vegadresseIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(34),
                            Adressekode(10100),
                            Adressenummernummer(2),
                        ),
                ),
            )

        val vegendring =
            Vegendring(
                FraEnTilMange(Fylkesnummer(12), listOf(Fylkesnummer(12))),
                FraEnTilMange(
                    Kommunenummer.Lopenummer(34),
                    listOf(Kommunenummer.Lopenummer(35), Kommunenummer.Lopenummer(36)),
                ),
                FraEnTilMange(Adressekode(10100), listOf(Adressekode(10100))),
            )

        test("Manglende regler for adresser") {
            val idGeneratorManager = mockIdGenerator(101)
            val sink = MockSink()

            transform(
                1,
                Reguleringsinput(
                    "abc",
                    Clock.System.todayIn(TimeZone.currentSystemDefault()),
                    listOf(
                        vegendring,
                    ),
                    emptyList(),
                    emptyList(),
                ),
                emptyList(),
                listOf(vegSource, vegadresseSource),
                emptyList(),
                listOf(sink),
                idGeneratorManager,
                TestStorage(),
                true,
            )

            val unresolvedVegadresseIdentType = getUnresolvedVegadresseIdentType()

            assertThat(sink::transformations).all {
                hasSize(4)
                index(0).all {
                    prop(Transformation::id).isEqualTo(vegId(101)) // Nygenerert
                    prop(Transformation::transformedIdent).isEqualTo(
                        adresseparsellIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(36),
                            Adressekode(10100),
                        ),
                    )
                    prop(Transformation::transformedAssociatedIdents).isNull()
                    prop(Transformation::resultObject).isNull()
                }
                index(1).all {
                    prop(Transformation::id).isEqualTo(vegId(1))
                    prop(Transformation::transformedIdent).isEqualTo(
                        adresseparsellIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(35),
                            Adressekode(10100),
                        ),
                    )
                    prop(Transformation::transformedAssociatedIdents).isNull()
                    prop(Transformation::resultObject).isNull()
                }
                index(2).all {
                    prop(Transformation::id).isEqualTo(vegadresseId(10))
                    prop(Transformation::transformedIdent).isEqualTo(
                        unresolvedVegadresseIdentType(
                            Adressenummernummer(1),
                        ),
                    )
                    prop(Transformation::transformedAssociatedIdents).isNull()
                    prop(Transformation::resultObject).isNull()
                }
                index(3).all {
                    prop(Transformation::id).isEqualTo(vegadresseId(11))
                    prop(Transformation::transformedIdent).isEqualTo(
                        unresolvedVegadresseIdentType(
                            Adressenummernummer(2),
                        ),
                    )
                    prop(Transformation::transformedAssociatedIdents).isNull()
                    prop(Transformation::resultObject).isNull()
                }
            }
        }

        test("Adresser fordelt") {
            val idGeneratorManager = mockIdGenerator(101)
            val sink = MockSink()

            transform(
                1,
                Reguleringsinput(
                    "abc",
                    Clock.System.todayIn(TimeZone.currentSystemDefault()),
                    listOf(
                        vegendring,
                        Vegadresseendring(
                            FraTil(Fylkesnummer(12), Fylkesnummer(12)),
                            FraTil(Kommunenummer.Lopenummer(34), Kommunenummer.Lopenummer(35)),
                            FraTil(Adressekode(10100), Adressekode(10100)),
                            FraTil(Adressenummernummer(1), Adressenummernummer(1)),
                        ),
                        Vegadresseendring(
                            FraTil(Fylkesnummer(12), Fylkesnummer(12)),
                            FraTil(Kommunenummer.Lopenummer(34), Kommunenummer.Lopenummer(36)),
                            FraTil(Adressekode(10100), Adressekode(10100)),
                            FraTil(Adressenummernummer(2), Adressenummernummer(1)),
                        ),
                    ),
                    emptyList(),
                    emptyList(),
                ),
                emptyList(),
                listOf(vegSource, vegadresseSource),
                emptyList(),
                listOf(sink),
                idGeneratorManager,
                TestStorage(),
                true,
            )

            assertThat(sink::transformations).all {
                hasSize(4)
                index(0).all {
                    prop(Transformation::id).isEqualTo(vegId(101)) // Nygenerert
                    prop(Transformation::transformedIdent).isEqualTo(
                        adresseparsellIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(36),
                            Adressekode(10100),
                        ),
                    )
                    prop(Transformation::transformedAssociatedIdents).isNull()
                    prop(Transformation::resultObject).isNull()
                }
                index(1).all {
                    prop(Transformation::id).isEqualTo(vegId(1))
                    prop(Transformation::transformedIdent).isEqualTo(
                        adresseparsellIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(35),
                            Adressekode(10100),
                        ),
                    )
                    prop(Transformation::transformedAssociatedIdents).isNull()
                    prop(Transformation::resultObject).isNull()
                }
                index(2).all {
                    prop(Transformation::id).isEqualTo(vegadresseId(10))
                    prop(Transformation::transformedIdent).isEqualTo(
                        vegadresseIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(35),
                            Adressekode(10100),
                            Adressenummernummer(1),
                        ),
                    )
                    prop(Transformation::transformedAssociatedIdents).isNull()
                    prop(Transformation::resultObject).isNull()
                }
                index(3).all {
                    prop(Transformation::id).isEqualTo(vegadresseId(11))
                    prop(Transformation::transformedIdent).isEqualTo(
                        vegadresseIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(36),
                            Adressekode(10100),
                            Adressenummernummer(1),
                        ),
                    )
                    prop(Transformation::transformedAssociatedIdents).isNull()
                    prop(Transformation::resultObject).isNull()
                }
            }
        }
    }
})
