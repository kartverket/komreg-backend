package no.kartverket.komreg.transformation

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.*
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.core.domain.Matrikkelenhet.GardsnummerserieIdent
import no.kartverket.komreg.core.domain.Matrikkelenhet.GrunneiendomIdent
import no.kartverket.komreg.core.domain.Matrikkelnummer.Bruksnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer
import no.kartverket.komreg.integration.spi.*

class TransformerMatrikkelenhetTest : FunSpec({
    fun matrikkelenhetId(idValue: Long) = Id(TestIdType.Foo, idValue) // Faktisk id-type er irrelevant

    test("Flytt hel kommune") {
        val idGeneratorManager = mockIdGenerator()

        val matrikkelenhetIdentType = getMatrikkelenhetIdentType()

        val source = mockSource(
            Entity(
                id = matrikkelenhetId(1),
                ident = matrikkelenhetIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Gardsnummer(1),
                    Bruksnummer(1),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                ),
            ),
            Entity(
                id = matrikkelenhetId(2),
                ident = matrikkelenhetIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Gardsnummer(1),
                    Bruksnummer(1),
                    Matrikkelnummer.Festenummer(1),
                    Matrikkelnummer.Seksjonsnummer(0),
                ),
            ),
        )

        val sink = MockSink()

        transform(
            1,
            emptyList(),
            listOf(source),
            emptyList(),
            listOf(sink),
            idGeneratorManager,
            TestStorage(),
            true,
            identTransformerImpl = IdentTransformerImpl(
                mapInput(
                    Reguleringsinput(
                        "abc",
                        Clock.System.todayIn(TimeZone.currentSystemDefault()),
                        listOf(
                            Kommuneendring(
                                FraEnTilMange(Fylkesnummer(12), listOf(Fylkesnummer(13))),
                                FraEnTilMange(
                                    Kommunenummer.Lopenummer(34),
                                    listOf(Kommunenummer.Lopenummer(24))
                                ),
                            ),
                        ),
                        emptyList(),
                        listOf(
                            Kommune(
                                Kommunenummer(1324),
                                Kommunenavn("Ny kommune"),
                                null,
                                Koordinatsystem.UTM32,
                                Koordinat(0.0, 0.0),
                                false,
                                "",
                                null,
                                null,
                                createDummyKommunevaapenImage('K'),
                            ),
                        ),
                    )
                )
            ),
            date = Reguleringsinput(
                "abc",
                Clock.System.todayIn(TimeZone.currentSystemDefault()),
                listOf(
                    Kommuneendring(
                        FraEnTilMange(Fylkesnummer(12), listOf(Fylkesnummer(13))),
                        FraEnTilMange(
                            Kommunenummer.Lopenummer(34),
                            listOf(Kommunenummer.Lopenummer(24))
                        ),
                    ),
                ),
                emptyList(),
                listOf(
                    Kommune(
                        Kommunenummer(1324),
                        Kommunenavn("Ny kommune"),
                        null,
                        Koordinatsystem.UTM32,
                        Koordinat(0.0, 0.0),
                        false,
                        "",
                        null,
                        null,
                        createDummyKommunevaapenImage('K'),
                    ),
                ),
            ).ikrafttredelsesdato,
        )

        assertThat(sink::transformations).all {
            hasSize(2)
            index(0).all {
                prop(Transformation::id).isEqualTo(matrikkelenhetId(1))
                prop(Transformation::transformedIdent).isEqualTo(
                    matrikkelenhetIdentType(
                        Fylkesnummer(13),
                        Kommunenummer.Lopenummer(24),
                        Gardsnummer(1),
                        Bruksnummer(1),
                        Matrikkelnummer.Festenummer(0),
                        Matrikkelnummer.Seksjonsnummer(0),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
            index(1).all {
                prop(Transformation::id).isEqualTo(matrikkelenhetId(2))
                prop(Transformation::transformedIdent).isEqualTo(
                    matrikkelenhetIdentType(
                        Fylkesnummer(13),
                        Kommunenummer.Lopenummer(24),
                        Gardsnummer(1),
                        Bruksnummer(1),
                        Matrikkelnummer.Festenummer(1),
                        Matrikkelnummer.Seksjonsnummer(0),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
        }
    }

    context("Del kommune") {
        val idGeneratorManager = mockIdGenerator()

        val matrikkelenhetIdentType = getMatrikkelenhetIdentType()

        val source = mockSource(
            Entity(
                id = matrikkelenhetId(1),
                ident = matrikkelenhetIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Gardsnummer(1),
                    Bruksnummer(1),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                ),
            ),
            Entity(
                id = matrikkelenhetId(2),
                ident = matrikkelenhetIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Gardsnummer(2),
                    Bruksnummer(1),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                ),
            ),
        )

        val kommuneendring = Kommuneendring(
            FraEnTilMange(Fylkesnummer(12), listOf(Fylkesnummer(12))),
            FraEnTilMange(
                Kommunenummer.Lopenummer(34),
                listOf(Kommunenummer.Lopenummer(35), Kommunenummer.Lopenummer(36)),
            ),
        )

        val nyeKommuner = listOf(
            Kommune(
                Kommunenummer(1235),
                Kommunenavn("Ny kommune 1"),
                null,
                Koordinatsystem.UTM32,
                Koordinat(0.0, 0.0),
                false,
                "",
                null,
                null,
                createDummyKommunevaapenImage('K'),
            ),
            Kommune(
                Kommunenummer(1236),
                Kommunenavn("Ny kommune 2"),
                null,
                Koordinatsystem.UTM32,
                Koordinat(0.0, 0.0),
                false,
                "",
                null,
                null,
                createDummyKommunevaapenImage('K'),
            ),
        )

        test("Manglende regel for matrikkelenhet") {
            val unresolvedMatrikkelenhetIdentType = getUnresolvedMatrikkelenhetIdentType()

            val sink = MockSink()

            transform(
                1,
                emptyList(),
                listOf(source),
                emptyList(),
                listOf(sink),
                idGeneratorManager,
                TestStorage(),
                true,
                identTransformerImpl = IdentTransformerImpl(
                    mapInput(
                        Reguleringsinput(
                            "abc",
                            Clock.System.todayIn(TimeZone.currentSystemDefault()),
                            listOf(
                                kommuneendring,
                                Matrikkelenhetendring(
                                    FraTil(Fylkesnummer(12), Fylkesnummer(12)),
                                    FraTil(
                                        Kommunenummer.Lopenummer(34),
                                        Kommunenummer.Lopenummer(35)
                                    ),
                                    Gardsnummer(1),
                                    Gardsnummer(1)
                                ),
                            ),
                            emptyList(),
                            nyeKommuner,
                        )
                    )
                ),
                date = Reguleringsinput(
                    "abc",
                    Clock.System.todayIn(TimeZone.currentSystemDefault()),
                    listOf(
                        kommuneendring,
                        Matrikkelenhetendring(
                            FraTil(Fylkesnummer(12), Fylkesnummer(12)),
                            FraTil(Kommunenummer.Lopenummer(34), Kommunenummer.Lopenummer(35)),
                            Gardsnummer(1),
                            Gardsnummer(1)
                        ),
                    ),
                    emptyList(),
                    nyeKommuner,
                ).ikrafttredelsesdato,
            )

            assertThat(sink::transformations).all {
                hasSize(2)
                index(0).all {
                    prop(Transformation::id).isEqualTo(matrikkelenhetId(1))
                    prop(Transformation::transformedIdent).isEqualTo(
                        matrikkelenhetIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(35),
                            Gardsnummer(1),
                            Bruksnummer(1),
                            Matrikkelnummer.Festenummer(0),
                            Matrikkelnummer.Seksjonsnummer(0),
                        ),
                    )
                    prop(Transformation::transformedAssociatedIdents).isNull()
                    prop(Transformation::resultObject).isNull()
                }
                index(1).all {
                    prop(Transformation::id).isEqualTo(matrikkelenhetId(2))
                    prop(Transformation::transformedIdent).isEqualTo(
                        unresolvedMatrikkelenhetIdentType(
                            Gardsnummer(2),
                            Bruksnummer(1),
                            Matrikkelnummer.Festenummer(0),
                            Matrikkelnummer.Seksjonsnummer(0),
                        ),
                    )
                    prop(Transformation::transformedAssociatedIdents).isNull()
                    prop(Transformation::resultObject).isNull()
                }
            }
        }

        test("Matrikkelenheter fordelt") {
            val sink = MockSink()

            transform(
                1,
                emptyList(),
                listOf(source),
                emptyList(),
                listOf(sink),
                idGeneratorManager,
                TestStorage(),
                true,
                identTransformerImpl = IdentTransformerImpl(
                    mapInput(
                        Reguleringsinput(
                            "abc",
                            Clock.System.todayIn(TimeZone.currentSystemDefault()),
                            listOf(
                                kommuneendring,
                                Matrikkelenhetendring(
                                    FraTil(Fylkesnummer(12), Fylkesnummer(12)),
                                    FraTil(
                                        Kommunenummer.Lopenummer(34),
                                        Kommunenummer.Lopenummer(35)
                                    ),
                                    Gardsnummer(1),
                                    Gardsnummer(1),
                                ),
                                Matrikkelenhetendring(
                                    FraTil(Fylkesnummer(12), Fylkesnummer(12)),
                                    FraTil(
                                        Kommunenummer.Lopenummer(34),
                                        Kommunenummer.Lopenummer(36)
                                    ),
                                    Gardsnummer(2),
                                    Gardsnummer(2),
                                ),
                            ),
                            emptyList(),
                            nyeKommuner,
                        )
                    )
                ),
                date = Reguleringsinput(
                    "abc",
                    Clock.System.todayIn(TimeZone.currentSystemDefault()),
                    listOf(
                        kommuneendring,
                        Matrikkelenhetendring(
                            FraTil(Fylkesnummer(12), Fylkesnummer(12)),
                            FraTil(Kommunenummer.Lopenummer(34), Kommunenummer.Lopenummer(35)),
                            Gardsnummer(1),
                            Gardsnummer(1),
                        ),
                        Matrikkelenhetendring(
                            FraTil(Fylkesnummer(12), Fylkesnummer(12)),
                            FraTil(Kommunenummer.Lopenummer(34), Kommunenummer.Lopenummer(36)),
                            Gardsnummer(2),
                            Gardsnummer(2),
                        ),
                    ),
                    emptyList(),
                    nyeKommuner,
                ).ikrafttredelsesdato,
            )

            assertThat(sink::transformations).all {
                hasSize(2)
                index(0).all {
                    prop(Transformation::id).isEqualTo(matrikkelenhetId(1))
                    prop(Transformation::transformedIdent).isEqualTo(
                        matrikkelenhetIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(35),
                            Gardsnummer(1),
                            Bruksnummer(1),
                            Matrikkelnummer.Festenummer(0),
                            Matrikkelnummer.Seksjonsnummer(0),
                        ),
                    )
                    prop(Transformation::transformedAssociatedIdents).isNull()
                    prop(Transformation::resultObject).isNull()
                }
                index(1).all {
                    prop(Transformation::id).isEqualTo(matrikkelenhetId(2))
                    prop(Transformation::transformedIdent).isEqualTo(
                        matrikkelenhetIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(36),
                            Gardsnummer(2),
                            Bruksnummer(1),
                            Matrikkelnummer.Festenummer(0),
                            Matrikkelnummer.Seksjonsnummer(0),
                        ),
                    )
                    prop(Transformation::transformedAssociatedIdents).isNull()
                    prop(Transformation::resultObject).isNull()
                }
            }
        }
    }

    context("Del matrikkelenhet") {
        val idGeneratorManager = mockIdGenerator()

        val fylke10: FylkeIdent = Fylke.Ident(Fylkesnummer(10))
        val fylke80: FylkeIdent = Fylke.Ident(Fylkesnummer(80))

        // Fra kommune 20
        val kommune20 = fylke10 / Kommunenummer.Lopenummer(20)
        val gardsnummerserie40 = kommune20 / Gardsnummer(40)
        val grunneiendom40_1 = gardsnummerserie40 / Bruksnummer(1)
        val grunneiendom40_2 = gardsnummerserie40 / Bruksnummer(2)
        val grunneiendom40_3 = gardsnummerserie40 / Bruksnummer(3)

        // Fra kommune 30
        val kommune30 = fylke10 / Kommunenummer.Lopenummer(30)
        val gardsnummerserie50 = kommune30 / Gardsnummer(50)
        val grunneiendom50_1 = gardsnummerserie50 / Bruksnummer(1)
        val grunneiendom50_2 = gardsnummerserie50 / Bruksnummer(2)
        val grunneiendom50_3 = gardsnummerserie50 / Bruksnummer(3)

        // Til kommune 90
        val kommune90 = fylke10 / Kommunenummer.Lopenummer(90)
        val gardsnummerserie60 = kommune90 / Gardsnummer(60)

        // Til kommune 99
        val kommune99 = fylke80 / Kommunenummer.Lopenummer(99)
        val gardsnummerserie70 = kommune99 / Gardsnummer(70)

        val nyeKommuner = listOf(
            Kommune(
                Kommunenummer(kommune99<Fylkesnummer>().value * 100L +  kommune99<Kommunenummer.Lopenummer>().value.toLong()),
                Kommunenavn("Ny kommune " + kommune99<Kommunenummer.Lopenummer>().value),
                null,
                Koordinatsystem.UTM32,
                Koordinat(0.0, 0.0),
                false,
                "",
                null,
                null,
                createDummyKommunevaapenImage('K'),
            )
        )

        val sourceIdents: List<Ident.And<*, *>> = listOf(
            grunneiendom40_1,
            grunneiendom40_2,
            grunneiendom40_3,
            grunneiendom50_1,
            grunneiendom50_2,
            grunneiendom50_3,
        )

        val gardsnummer40Idents: Set<GrunneiendomIdent> = sourceIdents
            .mapNotNull { it.convertToOrNull(GrunneiendomIdent) }
            .filterTo(HashSet()) { ident -> ident.dropLast().also { check(it.type == GardsnummerserieIdent) } == gardsnummerserie40 }

        suspend fun doTestTransform(
            endringer: Iterable<Endring>,
            sink: EntitySink
        ) {
            val reguleringsInput = Reguleringsinput(
                "abc",
                Clock.System.todayIn(TimeZone.currentSystemDefault()),
                endringer.toList(),
                emptyList(),
                nyeKommuner,
            )

            transform(
                1,
                emptyList(),
                listOf(
                    mockSource(
                        *(sourceIdents.mapIndexed { idValue, ident ->
                            Entity(matrikkelenhetId(idValue.toLong()), ident)
                        }.toTypedArray<Entity>())
                    )
                ),
                emptyList(),
                listOf(sink),
                idGeneratorManager,
                TestStorage(),
                true,
                identTransformerImpl = IdentTransformerImpl(mapInput(reguleringsInput)),
                date = reguleringsInput.ikrafttredelsesdato
            )
        }

        test("Del matrikkelenhet i 2 nye kommuner") {
            val sink = MockSink()
            val matrikkelenhetendring = Matrikkelenhetendring(
                FraTil(gardsnummerserie40<Fylkesnummer>(), gardsnummerserie60<Fylkesnummer>()),
                FraTil(gardsnummerserie40<Kommunenummer.Lopenummer>(), gardsnummerserie60<Kommunenummer.Lopenummer>()),
                gardsnummerserie40<Gardsnummer>(),
                gardsnummerserie60<Gardsnummer>(),
                mapOf(
                    grunneiendom40_1<Bruksnummer>() to gardsnummerserie70 / grunneiendom40_1<Bruksnummer>(),
                    grunneiendom40_2<Bruksnummer>() to gardsnummerserie60 / grunneiendom40_2<Bruksnummer>(),
                    grunneiendom40_3<Bruksnummer>() to gardsnummerserie60 / grunneiendom40_3<Bruksnummer>(),
                )
            )

            doTestTransform(listOf(matrikkelenhetendring), sink)

            // Alle bruksnummer for gardsnummer 40 skal transformeres til gardsnummer 60 (i ny kommune 90),
            // untatt bruksnummer 1 som skal transformeres til gardsnummer 70 (i ny kommune 99)
            val expected = gardsnummer40Idents.mapTo(HashSet()) {
                it to when (it.last) {
                    Bruksnummer(1) -> (gardsnummerserie70 / it.last)
                    else -> (gardsnummerserie60 / it.last)
                }
            }

            val actual = sink.transformations.mapTo(HashSet()) {
                val sourceIdent: GrunneiendomIdent? = it.sourceEntity?.ident.convertToOrNull(GrunneiendomIdent)
                val targetIdent: GrunneiendomIdent? = it.transformedIdent.convertToOrNull(GrunneiendomIdent)
                sourceIdent to targetIdent
            }

            assertThat(actual).isEqualTo(expected)
        }

        test("Del matrikkelenhet i 2 nye kommuner, ikke komplette parametere") {
            val sink = MockSink()
            val matrikkelenhetendring = Matrikkelenhetendring(
                FraTil(gardsnummerserie40<Fylkesnummer>(), gardsnummerserie60<Fylkesnummer>()),
                FraTil(gardsnummerserie40<Kommunenummer.Lopenummer>(), gardsnummerserie60<Kommunenummer.Lopenummer>()),
                gardsnummerserie40<Gardsnummer>(),
                gardsnummerserie60<Gardsnummer>(),
                mapOf(
                    grunneiendom40_1<Bruksnummer>() to gardsnummerserie70 / grunneiendom40_1<Bruksnummer>(),
                )
            )

            doTestTransform(listOf(matrikkelenhetendring), sink)

            val expected = gardsnummer40Idents.mapTo(HashSet()) { sourceIdent ->
                sourceIdent to when (sourceIdent.last) {
                    Bruksnummer(1) -> (gardsnummerserie70 / sourceIdent.last)
                    else -> null
                }
            }

            val actual = sink.transformations.mapTo(HashSet()) {
                val sourceIdent: GrunneiendomIdent? = it.sourceEntity?.ident.convertToOrNull(GrunneiendomIdent)
                val targetIdent: GrunneiendomIdent? = it.transformedIdent.convertToOrNull(GrunneiendomIdent)
                sourceIdent to targetIdent
            }

            assertThat(actual).isEqualTo(expected)
        }

        test("Splitt grunneiendom - feil på bevaring av gårdsnummer") {
            val sink = MockSink()
            val matrikkelenhetendring = Matrikkelenhetendring(
                FraTil(gardsnummerserie40<Fylkesnummer>(), gardsnummerserie40<Fylkesnummer>()),
                FraTil(gardsnummerserie40<Kommunenummer.Lopenummer>(), gardsnummerserie40<Kommunenummer.Lopenummer>()),
                gardsnummerserie40<Gardsnummer>(),
                null,
                mapOf(
                    grunneiendom40_1<Bruksnummer>() to gardsnummerserie70 / grunneiendom40_1<Bruksnummer>(),
                    grunneiendom40_2<Bruksnummer>() to gardsnummerserie60 / grunneiendom40_2<Bruksnummer>(),
                    grunneiendom40_3<Bruksnummer>() to gardsnummerserie40 / grunneiendom40_3<Bruksnummer>(),
                )
            )

            assertFailure { doTestTransform(listOf(matrikkelenhetendring), sink) }.messageContains("gårdsnummeret bevares for noen bruksnummer")
        }

        test("Flytt bruksnummer - bevar gårdsnummer") {
            val sink = MockSink()
            val matrikkelenhetendring = Matrikkelenhetendring(
                FraTil(gardsnummerserie40<Fylkesnummer>(), gardsnummerserie40<Fylkesnummer>()),
                FraTil(gardsnummerserie40<Kommunenummer.Lopenummer>(), gardsnummerserie40<Kommunenummer.Lopenummer>()),
                gardsnummerserie40<Gardsnummer>(),
                gardsnummerserie40<Gardsnummer>(),
                mapOf(
                    grunneiendom40_1<Bruksnummer>() to gardsnummerserie50 / Bruksnummer(4),
                    grunneiendom40_2<Bruksnummer>() to gardsnummerserie50 / Bruksnummer(5),
                )
            )

            doTestTransform(listOf(matrikkelenhetendring), sink)

            val expected = gardsnummer40Idents.mapNotNullTo(HashSet()) { sourceIdent ->
                when (sourceIdent.last) {
                    Bruksnummer(1) -> sourceIdent to  gardsnummerserie50 / Bruksnummer(4)
                    Bruksnummer(2) -> sourceIdent to  gardsnummerserie50 / Bruksnummer(5)
                    else -> null
                }
            }

            val actual = sink.transformations.mapTo(HashSet()) {
                val sourceIdent: GrunneiendomIdent? = it.sourceEntity?.ident.convertToOrNull(GrunneiendomIdent)
                val targetIdent: GrunneiendomIdent? = it.transformedIdent.convertToOrNull(GrunneiendomIdent)
                sourceIdent to targetIdent
            }

            assertThat(actual).isEqualTo(expected)
        }
    }

})
