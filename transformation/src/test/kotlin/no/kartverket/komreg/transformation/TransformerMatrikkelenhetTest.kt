package no.kartverket.komreg.transformation

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.*

class TransformerMatrikkelenhetTest : FunSpec({
    fun matrikkelenhetId(idValue: Long) = Id(TestIdType.Foo, idValue) // Faktisk id-type er irrelevant

    test("Flytt hel kommune") {
        val idGeneratorManager = mockIdGenerator()
        val kommuneService = mockk<KommuneService>()

        val matrikkelenhetIdentType = getMatrikkelenhetIdentType()

        val source = mockSource(
            Entity(
                id = matrikkelenhetId(1),
                ident = matrikkelenhetIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Matrikkelnummer.Gardsnummer(1),
                    Matrikkelnummer.Bruksnummer(1),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                ),
            ),
            Entity(
                id = matrikkelenhetId(2),
                ident = matrikkelenhetIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Matrikkelnummer.Gardsnummer(1),
                    Matrikkelnummer.Bruksnummer(1),
                    Matrikkelnummer.Festenummer(1),
                    Matrikkelnummer.Seksjonsnummer(0),
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
                    Kommuneendring(
                        FraTil(Fylkesnummer(12), Fylkesnummer(13)),
                        FraEnTilMange(Kommunenummer.Lopenummer(34), listOf(Kommunenummer.Lopenummer(24))),
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
                        null,
                    ),
                ),
            ),
            listOf(source),
            emptyList(),
            listOf(sink),
            idGeneratorManager,
            kommuneService,
            TestStorage(),
            true,
        )

        assertThat(sink::transformations).all {
            hasSize(2)
            index(0).all {
                prop(Transformation::id).isEqualTo(matrikkelenhetId(1))
                prop(Transformation::transformedIdent).isEqualTo(
                    matrikkelenhetIdentType(
                        Fylkesnummer(13),
                        Kommunenummer.Lopenummer(24),
                        Matrikkelnummer.Gardsnummer(1),
                        Matrikkelnummer.Bruksnummer(1),
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
                        Matrikkelnummer.Gardsnummer(1),
                        Matrikkelnummer.Bruksnummer(1),
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
        val kommuneService = mockk<KommuneService>()

        val matrikkelenhetIdentType = getMatrikkelenhetIdentType()

        val source = mockSource(
            Entity(
                id = matrikkelenhetId(1),
                ident = matrikkelenhetIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Matrikkelnummer.Gardsnummer(1),
                    Matrikkelnummer.Bruksnummer(1),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                ),
            ),
            Entity(
                id = matrikkelenhetId(2),
                ident = matrikkelenhetIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Matrikkelnummer.Gardsnummer(2),
                    Matrikkelnummer.Bruksnummer(1),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                ),
            ),
        )

        val kommuneendring = Kommuneendring(
            FraTil(Fylkesnummer(12), Fylkesnummer(12)),
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
                null,
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
                null,
            ),
        )

        test("Manglende regel for matrikkelenhet") {
            val unresolvedMatrikkelenhetIdentType = getUnresolvedMatrikkelenhetIdentType()

            val sink = MockSink()

            transform(
                1,
                Reguleringsinput(
                    "abc",
                    Clock.System.todayIn(TimeZone.currentSystemDefault()),
                    listOf(
                        kommuneendring,
                        Matrikkelenhetendring(
                            FraTil(Fylkesnummer(12), Fylkesnummer(12)),
                            FraTil(Kommunenummer.Lopenummer(34), Kommunenummer.Lopenummer(35)),
                            FraTil(Matrikkelnummer.Gardsnummer(1), Matrikkelnummer.Gardsnummer(1)),
                        ),
                    ),
                    emptyList(),
                    nyeKommuner,
                ),
                listOf(source),
                emptyList(),
                listOf(sink),
                idGeneratorManager,
                kommuneService,
                TestStorage(),
                true,
            )

            assertThat(sink::transformations).all {
                hasSize(2)
                index(0).all {
                    prop(Transformation::id).isEqualTo(matrikkelenhetId(1))
                    prop(Transformation::transformedIdent).isEqualTo(
                        matrikkelenhetIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(35),
                            Matrikkelnummer.Gardsnummer(1),
                            Matrikkelnummer.Bruksnummer(1),
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
                            Matrikkelnummer.Gardsnummer(2),
                            Matrikkelnummer.Bruksnummer(1),
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
                Reguleringsinput(
                    "abc",
                    Clock.System.todayIn(TimeZone.currentSystemDefault()),
                    listOf(
                        kommuneendring,
                        Matrikkelenhetendring(
                            FraTil(Fylkesnummer(12), Fylkesnummer(12)),
                            FraTil(Kommunenummer.Lopenummer(34), Kommunenummer.Lopenummer(35)),
                            FraTil(Matrikkelnummer.Gardsnummer(1), Matrikkelnummer.Gardsnummer(1)),
                        ),
                        Matrikkelenhetendring(
                            FraTil(Fylkesnummer(12), Fylkesnummer(12)),
                            FraTil(Kommunenummer.Lopenummer(34), Kommunenummer.Lopenummer(36)),
                            FraTil(Matrikkelnummer.Gardsnummer(2), Matrikkelnummer.Gardsnummer(2)),
                        ),
                    ),
                    emptyList(),
                    nyeKommuner,
                ),
                listOf(source),
                emptyList(),
                listOf(sink),
                idGeneratorManager,
                kommuneService,
                TestStorage(),
                true,
            )

            assertThat(sink::transformations).all {
                hasSize(2)
                index(0).all {
                    prop(Transformation::id).isEqualTo(matrikkelenhetId(1))
                    prop(Transformation::transformedIdent).isEqualTo(
                        matrikkelenhetIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(35),
                            Matrikkelnummer.Gardsnummer(1),
                            Matrikkelnummer.Bruksnummer(1),
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
                            Matrikkelnummer.Gardsnummer(2),
                            Matrikkelnummer.Bruksnummer(1),
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
})
