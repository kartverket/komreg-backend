package no.kartverket.komreg.transformation

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.core.domain.Matrikkelnummer.Bruksnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer
import no.kartverket.komreg.integration.spi.*

class TransformerKommuneTest : FunSpec({
    test("Flytt kommune mellom fylker") {
        val idGeneratorManager = mockIdGenerator()

        val kommuneIdentType = getKommuneIdentType()

        val source = mockSource(
            Entity(
                id = Id(TestIdType.Kommune, 1234),
                ident = kommuneIdentType(Fylkesnummer(12), Kommunenummer.Lopenummer(34)),
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
                        FraEnTilMange(Kommunenummer.Lopenummer(34), listOf(Kommunenummer.Lopenummer(35))),
                    ),
                ),
                emptyList(),
                listOf(
                    Kommune(
                        Kommunenummer(1335),
                        Kommunenavn("Ny kommune"),
                        null,
                        Koordinatsystem.UTM32,
                        Koordinat(569682.0, 6670739.0),
                        false,
                        "1-10",
                        null,
                        null,
                        null,
                    ),
                ),
            ),
            emptyList(),
            listOf(source),
            emptyList(),
            listOf(sink),
            idGeneratorManager,
            TestStorage(),
            true,
        )

        assertThat(sink::transformations).apply {
            hasSize(2)
            index(0).all {
                prop(Transformation::id).isEqualTo(Id(TestIdType.Kommune, 1335))
                prop(Transformation::transformedIdent).isEqualTo(kommuneIdentType(Fylkesnummer(13), Kommunenummer.Lopenummer(35)))
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject)
                    .isNotNull()
                    .isInstanceOf(Kommunedata::class)
                    .all {
                        prop(Kommunedata::navn).isEqualTo("NY KOMMUNE")
                        prop(Kommunedata::koordinatsystem).isEqualTo(Koordinatsystem.UTM32)
                        prop(Kommunedata::senterpunkt).isEqualTo(Koordinat(569682.0, 6670739.0))
                        prop(Kommunedata::godkjenteGardsnumre).isEqualTo("1-10")
                    }
            }
            index(1).all {
                prop(Transformation::id).isEqualTo(Id(TestIdType.Kommune, 1234))
                prop(Transformation::transformedIdent).isEqualTo(kommuneIdentType(Fylkesnummer(13), Kommunenummer.Lopenummer(35)))
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
        }
    }

    test("Slå sammen kommuner") {
        val idGeneratorManager = mockIdGenerator()

        val kommuneIdentType = getKommuneIdentType()
        val matrikkelenhetIdentType = getMatrikkelenhetIdentType()

        val source = mockSource(
            Entity(
                id = Id(TestIdType.Kommune, 1234),
                ident = kommuneIdentType(Fylkesnummer(12), Kommunenummer.Lopenummer(34)),
            ),
            Entity(
                id = Id(TestIdType.Kommune, 1236),
                ident = kommuneIdentType(Fylkesnummer(12), Kommunenummer.Lopenummer(36)),
            ),
            Entity(
                id = Id(TestIdType.Foo, 1),
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
                id = Id(TestIdType.Foo, 2),
                ident = matrikkelenhetIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(36),
                    Gardsnummer(2),
                    Bruksnummer(1),
                    Matrikkelnummer.Festenummer(0),
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
                        FraEnTilMange(Fylkesnummer(12), listOf(Fylkesnummer(13))),
                        FraEnTilMange(Kommunenummer.Lopenummer(34), listOf(Kommunenummer.Lopenummer(35))),
                        sammenslaaing = false,
                    ),
                    Kommuneendring(
                        FraEnTilMange(Fylkesnummer(12), listOf(Fylkesnummer(13))),
                        FraEnTilMange(Kommunenummer.Lopenummer(36), listOf(Kommunenummer.Lopenummer(35))),
                        sammenslaaing = true, //sammenslåing = true for kommune nr 2 som skal slås inn i en annen kommune
                    ),
                ),
                emptyList(),
                listOf(
                    Kommune(
                        Kommunenummer(1335),
                        Kommunenavn("Ny kommune"),
                        null,
                        Koordinatsystem.UTM32,
                        Koordinat(569682.0, 6670739.0),
                        false,
                        "1-10",
                        null,
                        null,
                        null,
                    ),
                ),
            ),
            emptyList(),
            listOf(source),
            emptyList(),
            listOf(sink),
            idGeneratorManager,
            TestStorage(),
            true,
        )

        assertThat(sink::transformations).apply {
            hasSize(5)
            index(0).all {
                prop(Transformation::id).isEqualTo(Id(TestIdType.Kommune, 1335))
                prop(Transformation::transformedIdent).isEqualTo(kommuneIdentType(Fylkesnummer(13), Kommunenummer.Lopenummer(35)))
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject)
                    .isNotNull()
                    .isInstanceOf(Kommunedata::class)
                    .all {
                        prop(Kommunedata::navn).isEqualTo("NY KOMMUNE")
                        prop(Kommunedata::koordinatsystem).isEqualTo(Koordinatsystem.UTM32)
                        prop(Kommunedata::senterpunkt).isEqualTo(Koordinat(569682.0, 6670739.0))
                        prop(Kommunedata::godkjenteGardsnumre).isEqualTo("1-10")
                    }
            }
            index(1).all {
                prop(Transformation::id).isEqualTo(Id(TestIdType.Kommune, 1234))
                prop(Transformation::transformedIdent).isEqualTo(kommuneIdentType(Fylkesnummer(13), Kommunenummer.Lopenummer(35)))
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
            index(2).all {
                prop(Transformation::id).isEqualTo(Id(TestIdType.Kommune, 1236))
                prop(Transformation::transformedIdent).isEqualTo(kommuneIdentType(Fylkesnummer(13), Kommunenummer.Lopenummer(35)))
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
            index(3).all {
                prop(Transformation::id).isEqualTo(Id(TestIdType.Foo, 1))
                prop(Transformation::transformedIdent).isEqualTo(matrikkelenhetIdentType(
                    Fylkesnummer(13),
                    Kommunenummer.Lopenummer(35),
                    Gardsnummer(1),
                    Bruksnummer(1),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                ))
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
            index(4).all {
                prop(Transformation::id).isEqualTo(Id(TestIdType.Foo, 2))
                prop(Transformation::transformedIdent).isEqualTo(matrikkelenhetIdentType(
                    Fylkesnummer(13),
                    Kommunenummer.Lopenummer(35),
                    Gardsnummer(2),
                    Bruksnummer(1),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                ))
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
        }
    }

    test("Splitt kommune") {
        val idGeneratorManager = mockIdGenerator()

        val kommuneIdentType = identTypeOf2<Fylkesnummer, Kommunenummer.Lopenummer>()

        val source = mockSource(
            Entity(
                id = Id(TestIdType.Kommune, 1234),
                ident = kommuneIdentType(Fylkesnummer(12), Kommunenummer.Lopenummer(34)),
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
                        FraEnTilMange(Fylkesnummer(12), listOf(Fylkesnummer(12))),
                        FraEnTilMange(
                            Kommunenummer.Lopenummer(34),
                            listOf(
                                Kommunenummer.Lopenummer(35),
                                Kommunenummer.Lopenummer(36),
                            ),
                        ),
                    ),
                ),
                emptyList(),
                listOf(
                    Kommune(
                        Kommunenummer(1235),
                        Kommunenavn("Ny kommune 1"),
                        null,
                        Koordinatsystem.UTM32,
                        Koordinat(569682.0, 6670739.0),
                        false,
                        "1-10",
                        null,
                        null,
                        null,
                    ),
                    Kommune(
                        Kommunenummer(1236),
                        Kommunenavn("Ny kommune 2"),
                        null,
                        Koordinatsystem.UTM32,
                        Koordinat(569682.0, 6671739.0),
                        false,
                        "11-20",
                        null,
                        null,
                        null,
                    ),
                ),
            ),
            emptyList(),
            listOf(source),
            emptyList(),
            listOf(sink),
            idGeneratorManager,
            TestStorage(),
            true,
        )

        assertThat(sink::transformations).apply {
            hasSize(3)
            index(0).all {
                prop(Transformation::id).isEqualTo(Id(TestIdType.Kommune, 1235))
                prop(Transformation::transformedIdent).isEqualTo(kommuneIdentType(Fylkesnummer(12), Kommunenummer.Lopenummer(35)))
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject)
                    .isNotNull()
                    .isInstanceOf(Kommunedata::class)
                    .all {
                        prop(Kommunedata::navn).isEqualTo("NY KOMMUNE 1")
                        prop(Kommunedata::koordinatsystem).isEqualTo(Koordinatsystem.UTM32)
                        prop(Kommunedata::senterpunkt).isEqualTo(Koordinat(569682.0, 6670739.0))
                        prop(Kommunedata::godkjenteGardsnumre).isEqualTo("1-10")
                    }
            }
            index(1).all {
                prop(Transformation::id).isEqualTo(Id(TestIdType.Kommune, 1236))
                prop(Transformation::transformedIdent).isEqualTo(kommuneIdentType(Fylkesnummer(12), Kommunenummer.Lopenummer(36)))
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject)
                    .isNotNull()
                    .isInstanceOf(Kommunedata::class)
                    .all {
                        prop(Kommunedata::navn).isEqualTo("NY KOMMUNE 2")
                        prop(Kommunedata::koordinatsystem).isEqualTo(Koordinatsystem.UTM32)
                        prop(Kommunedata::senterpunkt).isEqualTo(Koordinat(569682.0, 6671739.0))
                        prop(Kommunedata::godkjenteGardsnumre).isEqualTo("11-20")
                    }
            }
            index(2).all {
                prop(Transformation::id).isEqualTo(Id(TestIdType.Kommune, 1234))
                prop(Transformation::transformedIdent).isEqualTo(kommuneIdentType(Fylkesnummer(12), Kommunenummer.Lopenummer(35)))
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
        }
    }
})
