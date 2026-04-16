package no.kartverket.komreg.transformation

import io.kotest.core.spec.style.FunSpec
import no.kartverket.komreg.core.domain.Id
import assertk.all
import assertk.assertThat
import assertk.assertions.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.integration.spi.invoke

class TransformerKulturminneTest: FunSpec({
    fun lokalitetsnummer(idValue: Long) = Id(TestIdType.Foo, idValue) // Faktisk id-type er irrelevant

    test("Slå sammen mnr mangler") {
        val idGeneratorManager = mockIdGenerator()

        val kulturminneIdentType = getKulturminneIdentType()
        val kommuneIdentType = getKommuneIdentType()


        val source = mockSource(
            Entity(
                id = Id(TestIdType.Kommune, 1334),
                ident = kommuneIdentType(Fylkesnummer(13), Kommunenummer.Lopenummer(34)),
            ),
            Entity(
                id = Id(TestIdType.Kommune, 1336),
                ident = kommuneIdentType(Fylkesnummer(13), Kommunenummer.Lopenummer(36)),
            ),
            Entity(
                id = lokalitetsnummer(1),
                ident = kulturminneIdentType(
                    Fylkesnummer(13),
                    Kommunenummer.Lopenummer(34),
                    Matrikkelnummer.Gardsnummer(0),
                    Matrikkelnummer.Bruksnummer(0),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                    Lokalitetsnummer(1),
                ),
            ),
            Entity(
                id = lokalitetsnummer(3),
                ident = kulturminneIdentType(
                    Fylkesnummer(13),
                    Kommunenummer.Lopenummer(36),
                    Matrikkelnummer.Gardsnummer(0),
                    Matrikkelnummer.Bruksnummer(0),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                    Lokalitetsnummer(3),
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
                        FraEnTilMange(Fylkesnummer(13), listOf(Fylkesnummer(13))),
                        FraEnTilMange(Kommunenummer.Lopenummer(34), listOf(Kommunenummer.Lopenummer(35))),
                        sammenslaaing = false,
                    ),
                    Kommuneendring(
                        FraEnTilMange(Fylkesnummer(13), listOf(Fylkesnummer(13))),
                        FraEnTilMange(Kommunenummer.Lopenummer(36), listOf(Kommunenummer.Lopenummer(35))),
                        sammenslaaing = true, //sammenslåing = true for kommune nr 2 som skal slås inn i en annen kommune
                    ),
                    Kulturminneendring(
                        FraTil(Fylkesnummer(13), Fylkesnummer(13)),
                        FraTil(Kommunenummer.Lopenummer(34), Kommunenummer.Lopenummer(35)),
                        FraTil(Lokalitetsnummer(1), Lokalitetsnummer(1))

                    ),
                    Kulturminneendring(
                        FraTil(Fylkesnummer(13), Fylkesnummer(13)),
                        FraTil(Kommunenummer.Lopenummer(36), Kommunenummer.Lopenummer(35)),
                        FraTil(Lokalitetsnummer(3), Lokalitetsnummer(3))
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

        assertThat(sink::transformations).all {
            hasSize(5)
            index(0).all {
                prop(Transformation::transformedIdent).isEqualTo(
                    kommuneIdentType(
                        Fylkesnummer(13),
                        Kommunenummer.Lopenummer(35),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNotNull()
            }
            index(1).all {
                prop(Transformation::transformedIdent).isEqualTo(
                    kommuneIdentType(
                        Fylkesnummer(13),
                        Kommunenummer.Lopenummer(35),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
            index(2).all {
                prop(Transformation::transformedIdent).isEqualTo(
                    kommuneIdentType(
                        Fylkesnummer(13),
                        Kommunenummer.Lopenummer(35),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
            index(3).all {
                prop(Transformation::transformedIdent).isEqualTo(
                    kulturminneIdentType(
                        Fylkesnummer(13),
                        Kommunenummer.Lopenummer(35),
                        Matrikkelnummer.Gardsnummer(0),
                        Matrikkelnummer.Bruksnummer(0),
                        Matrikkelnummer.Festenummer(0),
                        Matrikkelnummer.Seksjonsnummer(0),
                        Lokalitetsnummer(1),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
            index(4).all {
                prop(Transformation::transformedIdent).isEqualTo(
                    kulturminneIdentType(
                        Fylkesnummer(13),
                        Kommunenummer.Lopenummer(35),
                        Matrikkelnummer.Gardsnummer(0),
                        Matrikkelnummer.Bruksnummer(0),
                        Matrikkelnummer.Festenummer(0),
                        Matrikkelnummer.Seksjonsnummer(0),
                        Lokalitetsnummer(3),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
        }
    }
})