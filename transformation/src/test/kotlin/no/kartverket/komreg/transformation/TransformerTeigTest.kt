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

class TransformerTeigTest : FunSpec({
    fun teigId(idValue: Long) = Id(TestIdType.Foo, idValue) // Faktisk id-type er irrelevant

    test("Flytt hel kommune") {
        val idGeneratorManager = mockIdGenerator()
        val kommuneService = mockk<KommuneService>()

        val teigIdentType = getTeigIdentType()

        val source = mockSource(
            Entity(
                id = teigId(1),
                ident = teigIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Matrikkelnummer.Gardsnummer(1),
                    Matrikkelnummer.Bruksnummer(1),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                    TeigId(2)
                )
            ),
            Entity(
                id = teigId(3),
                ident = teigIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Matrikkelnummer.Gardsnummer(0),
                    Matrikkelnummer.Bruksnummer(0),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                    TeigId(4)
                )
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
                        FraEnTilMange(Kommunenummer.Lopenummer(34), listOf(Kommunenummer.Lopenummer(24)))
                    )
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
                        null
                    )
                )
            ),
            listOf(source),
            emptyList(),
            listOf(sink),
            idGeneratorManager,
            kommuneService,
            TestStorage()
        )

        assertThat(sink::transformations).all {
            hasSize(2)
            index(0).all {
                prop(Transformation::id).isEqualTo(teigId(1))
                prop(Transformation::transformedIdent).isEqualTo(teigIdentType(
                    Fylkesnummer(13),
                    Kommunenummer.Lopenummer(24),
                    Matrikkelnummer.Gardsnummer(1),
                    Matrikkelnummer.Bruksnummer(1),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                    TeigId(2)
                ))
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
            index(1).all {
                prop(Transformation::id).isEqualTo(teigId(3))
                prop(Transformation::transformedIdent).isEqualTo(teigIdentType(
                    Fylkesnummer(13),
                    Kommunenummer.Lopenummer(24),
                    Matrikkelnummer.Gardsnummer(0),
                    Matrikkelnummer.Bruksnummer(0),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                    TeigId(4)
                ))
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
        }
    }

    test("Fordel mnr mangler") {
        val idGeneratorManager = mockIdGenerator()
        val kommuneService = mockk<KommuneService>()

        val teigIdentType = getTeigIdentType()

        val source = mockSource(
            Entity(
                id = teigId(1),
                ident = teigIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Matrikkelnummer.Gardsnummer(0),
                    Matrikkelnummer.Bruksnummer(0),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                    TeigId(2)
                )
            ),
            Entity(
                id = teigId(3),
                ident = teigIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Matrikkelnummer.Gardsnummer(0),
                    Matrikkelnummer.Bruksnummer(0),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                    TeigId(4)
                )
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
                        FraEnTilMange(
                            Kommunenummer.Lopenummer(34),
                            listOf(Kommunenummer.Lopenummer(24), Kommunenummer.Lopenummer(25))
                        )
                    ),
                    Teigendring(
                        FraTil(Fylkesnummer(12), Fylkesnummer(13)),
                        FraTil(Kommunenummer.Lopenummer(34), Kommunenummer.Lopenummer(24)),
                        FraTil(TeigId(2), TeigId(2))
                    ),
                    Teigendring(
                        FraTil(Fylkesnummer(12), Fylkesnummer(13)),
                        FraTil(Kommunenummer.Lopenummer(34), Kommunenummer.Lopenummer(25)),
                        FraTil(TeigId(4), TeigId(4))
                    )
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
                        null
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
                        null
                    )
                )
            ),
            listOf(source),
            emptyList(),
            listOf(sink),
            idGeneratorManager,
            kommuneService,
            TestStorage()
        )

        assertThat(sink::transformations).all {
            hasSize(2)
            index(0).all {
                prop(Transformation::id).isEqualTo(teigId(1))
                prop(Transformation::transformedIdent).isEqualTo(teigIdentType(
                    Fylkesnummer(13),
                    Kommunenummer.Lopenummer(24),
                    Matrikkelnummer.Gardsnummer(0),
                    Matrikkelnummer.Bruksnummer(0),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                    TeigId(2)
                ))
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
            index(1).all {
                prop(Transformation::id).isEqualTo(teigId(3))
                prop(Transformation::transformedIdent).isEqualTo(teigIdentType(
                    Fylkesnummer(13),
                    Kommunenummer.Lopenummer(25),
                    Matrikkelnummer.Gardsnummer(0),
                    Matrikkelnummer.Bruksnummer(0),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                    TeigId(4)
                ))
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
        }
    }
})
