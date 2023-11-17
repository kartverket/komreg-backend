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
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.KommuneService
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.integration.spi.invoke

class TransformerBygningTest : FunSpec({
    fun bygningId(idValue: Long) = Id(TestIdType.Foo, idValue) // Faktisk id-type er irrelevant

    test("Flytt hel kommune") {
        val idGeneratorManager = mockIdGenerator()
        val kommuneService = mockk<KommuneService>()

        val bygningIdentType = getBygningIdentType()
        val matrikkelenhetIdentType = getMatrikkelenhetIdentType()

        val byggSource = mockSource(
            Entity(
                id = bygningId(1),
                ident = bygningIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Bygningsnummer(1),
                ),
            ),
            Entity(
                id = bygningId(2),
                ident = bygningIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Bygningsnummer(2),
                ),
                associatedIdents = setOf(
                    matrikkelenhetIdentType(
                        Fylkesnummer(12),
                        Kommunenummer.Lopenummer(34),
                        Matrikkelnummer.Gardsnummer(1),
                        Matrikkelnummer.Bruksnummer(1),
                        Matrikkelnummer.Festenummer(0),
                        Matrikkelnummer.Seksjonsnummer(0),
                    ),
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
                        FraEnTilMange(Fylkesnummer(12), listOf(Fylkesnummer(13))),
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
            listOf(byggSource),
            emptyList(),
            listOf(sink),
            idGeneratorManager,
            TestStorage(),
            true,
        )

        assertThat(sink::transformations).all {
            hasSize(2)
            index(0).all {
                prop(Transformation::id).isEqualTo(bygningId(1))
                prop(Transformation::transformedIdent).isEqualTo(
                    bygningIdentType(
                        Fylkesnummer(13),
                        Kommunenummer.Lopenummer(24),
                        Bygningsnummer(1),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
            index(1).all {
                prop(Transformation::id).isEqualTo(bygningId(2))
                prop(Transformation::transformedIdent).isEqualTo(
                    bygningIdentType(
                        Fylkesnummer(13),
                        Kommunenummer.Lopenummer(24),
                        Bygningsnummer(2),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNotNull()
                    .containsOnly(
                        matrikkelenhetIdentType(
                            Fylkesnummer(13),
                            Kommunenummer.Lopenummer(24),
                            Matrikkelnummer.Gardsnummer(1),
                            Matrikkelnummer.Bruksnummer(1),
                            Matrikkelnummer.Festenummer(0),
                            Matrikkelnummer.Seksjonsnummer(0),
                        ),
                    )
                prop(Transformation::resultObject).isNull()
            }
        }
    }

    context("Splitt kommune") {
        val idGeneratorManager = mockIdGenerator()
        val kommuneService = mockk<KommuneService>()

        val bygningIdentType = getBygningIdentType()
        val matrikkelenhetIdentType = getMatrikkelenhetIdentType()

        val input = Reguleringsinput(
            "abc",
            Clock.System.todayIn(TimeZone.currentSystemDefault()),
            listOf(
                Kommuneendring(
                    FraEnTilMange(Fylkesnummer(12), listOf(Fylkesnummer(13))),
                    FraEnTilMange(
                        Kommunenummer.Lopenummer(34),
                        listOf(Kommunenummer.Lopenummer(24), Kommunenummer.Lopenummer(25)),
                    ),
                ),
                Matrikkelenhetendring(
                    FraEnTilMange(Fylkesnummer(12), listOf(Fylkesnummer(13))),
                    FraTil(Kommunenummer.Lopenummer(34), Kommunenummer.Lopenummer(24)),
                    FraTil(Matrikkelnummer.Gardsnummer(1), Matrikkelnummer.Gardsnummer(1)),
                ),
            ),
            emptyList(),
            listOf(
                Kommune(
                    Kommunenummer(1324),
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
                    Kommunenummer(1325),
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
            ),
        )

        val unresolvedBygningIdentType = getUnresolvedBygningIdentType()

        test("Bygning uten koblinger er tvetydig") {
            val byggSource = mockSource(
                Entity(
                    id = bygningId(1),
                    ident = bygningIdentType(
                        Fylkesnummer(12),
                        Kommunenummer.Lopenummer(34),
                        Bygningsnummer(1),
                    ),
                ),
            )

            val sink = MockSink()

            transform(
                1,
                input,
                listOf(byggSource),
                emptyList(),
                listOf(sink),
                idGeneratorManager,
                TestStorage(),
                true,
            )

            assertThat(sink::transformations).all {
                hasSize(1)
                index(0).all {
                    prop(Transformation::id).isEqualTo(bygningId(1))
                    prop(Transformation::transformedIdent).isEqualTo(
                        unresolvedBygningIdentType(
                            Bygningsnummer(1),
                        ),
                    )
                    prop(Transformation::transformedAssociatedIdents).isNull()
                    prop(Transformation::resultObject).isNull()
                }
            }
        }

        test("Bygning med kobling får entydig kobling") {
            val byggSource = mockSource(
                Entity(
                    id = bygningId(2),
                    ident = bygningIdentType(
                        Fylkesnummer(12),
                        Kommunenummer.Lopenummer(34),
                        Bygningsnummer(2),
                    ),
                    associatedIdents = setOf(
                        matrikkelenhetIdentType(
                            Fylkesnummer(12),
                            Kommunenummer.Lopenummer(34),
                            Matrikkelnummer.Gardsnummer(1),
                            Matrikkelnummer.Bruksnummer(1),
                            Matrikkelnummer.Festenummer(0),
                            Matrikkelnummer.Seksjonsnummer(0),
                        ),
                    ),
                ),
            )

            val sink = MockSink()

            transform(
                1,
                input,
                listOf(byggSource),
                emptyList(),
                listOf(sink),
                idGeneratorManager,
                TestStorage(),
                true,
            )

            assertThat(sink::transformations).all {
                hasSize(1)
                index(0).all {
                    prop(Transformation::id).isEqualTo(bygningId(2))
                    prop(Transformation::transformedIdent).isEqualTo(
                        unresolvedBygningIdentType(
                            Bygningsnummer(2),
                        ),
                    )
                    prop(Transformation::transformedAssociatedIdents).isNotNull()
                        .containsOnly(
                            matrikkelenhetIdentType(
                                Fylkesnummer(13),
                                Kommunenummer.Lopenummer(24),
                                Matrikkelnummer.Gardsnummer(1),
                                Matrikkelnummer.Bruksnummer(1),
                                Matrikkelnummer.Festenummer(0),
                                Matrikkelnummer.Seksjonsnummer(0),
                            ),
                        )
                    prop(Transformation::resultObject).isNull()
                }
            }
        }
    }
})
