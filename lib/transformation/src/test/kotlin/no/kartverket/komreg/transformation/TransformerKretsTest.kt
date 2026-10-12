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

class TransformerKretsTest : FunSpec({
    fun kretsId(idValue: Long) = Id(TestIdType.Foo, idValue) // Faktisk id-type er irrelevant

    test("Flytt hel kommune") {
        val idGeneratorManager = mockIdGenerator()

        val kretsIdentType = getKretsIdentType()

        val source = mockSource(
            Entity(
                id = kretsId(1),
                ident = kretsIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Kretstype("G"),
                    Kretsnummer(1),
                ),
            ),
            Entity(
                id = kretsId(2),
                ident = kretsIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Kretstype("1"),
                    Kretsnummer(101),
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
                        createDummyKommunevaapenImage('K'),
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
            hasSize(2)
            index(0).all {
                prop(Transformation::id).isEqualTo(kretsId(1))
                prop(Transformation::transformedIdent).isEqualTo(
                    kretsIdentType(
                        Fylkesnummer(13),
                        Kommunenummer.Lopenummer(24),
                        Kretstype("G"),
                        Kretsnummer(1),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
            index(1).all {
                prop(Transformation::id).isEqualTo(kretsId(2))
                prop(Transformation::transformedIdent).isEqualTo(
                    kretsIdentType(
                        Fylkesnummer(13),
                        Kommunenummer.Lopenummer(24),
                        Kretstype("1"),
                        Kretsnummer(101),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
        }
    }

    test("Grensejuster en hel krets") {
        val idGeneratorManager = mockIdGenerator()

        val kretsIdentType = getKretsIdentType()

        val source = mockSource(
            Entity(
                id = kretsId(1),
                ident = kretsIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Kretstype("G"),
                    Kretsnummer(1),
                ),
            ),
            Entity(
                id = kretsId(2),
                ident = kretsIdentType(
                    Fylkesnummer(12),
                    Kommunenummer.Lopenummer(34),
                    Kretstype("G"),
                    Kretsnummer(2),
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
                    Kretsendring(
                        FraTil(Fylkesnummer(12), Fylkesnummer(13)),
                        FraTil(Kommunenummer.Lopenummer(34), Kommunenummer.Lopenummer(24)),
                        kretstype = FraTil(Kretstype("G"), Kretstype("G")),
                        kretsnummer = FraTil(Kretsnummer(2), Kretsnummer(10)),
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
            hasSize(1)
            index(0).all {
                prop(Transformation::id).isEqualTo(kretsId(2))
                prop(Transformation::transformedIdent).isEqualTo(
                    kretsIdentType(
                        Fylkesnummer(13),
                        Kommunenummer.Lopenummer(24),
                        Kretstype("G"),
                        Kretsnummer(10),
                    ),
                )
                prop(Transformation::transformedAssociatedIdents).isNull()
                prop(Transformation::resultObject).isNull()
            }
        }
    }
})
