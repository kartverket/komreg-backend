package no.kartverket.komreg.transformation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import kotlinx.coroutines.flow.toList
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.transformation.parameters.BruksnummerParameter
import no.kartverket.komreg.transformation.parameters.GardsnummerParameter

class GrensejusteringTests : FunSpec({
    test("Flytt et helt gårdsnummer") {
        val transformer = ParameterbasedTransformer(
            listOf(
                GardsnummerParameter(
                    Kommunenummer(9911), IntRange(2, 2),
                    Kommunenummer(9912), 15
                ),
            )
        )

        val entitySource = mockEntitySource {
            entity(
                "Matrikkelenhet:1",
                ident = matrikkelnummer("9911", 1, 1)
            )
            entity(
                "Matrikkelenhet:2",
                ident = matrikkelnummer("9911", 2, 1)
            )
            entity(
                "Matrikkelenhet:3",
                ident = matrikkelnummer("9911", 2, 2)
            )
            entity(
                "Matrikkelenhet:4",
                ident = matrikkelnummer("9911", 3, 1)
            )
            entity(
                "Matrikkelenhet:5",
                ident = matrikkelnummer("9912", 1, 1)
            )
        }

        val transformedFlow = transformer.transform(entitySource.entityFlow)

        transformedFlow.toList()
            .associateBy({ it.id}, { it.transformedIdent })
            .shouldContainExactly(
                mapOf(
                    "Matrikkelenhet:2" to matrikkelnummer("9912", 17, 1),
                    "Matrikkelenhet:3" to matrikkelnummer("9912", 17, 2),
                )
            )
    }

    test("Flytt kun et bruksnummer") {
        val transformer = ParameterbasedTransformer(
            listOf(
                BruksnummerParameter(
                    Kommunenummer(9911), Matrikkelnummer.Gardsnummer(2), IntRange(2, 2),
                    Kommunenummer(9912), Matrikkelnummer.Gardsnummer(50), -1
                ),
            )
        )

        val entitySource = mockEntitySource {
            entity(
                "Matrikkelenhet:1",
                ident = matrikkelnummer("9911", 1, 1)
            )
            entity(
                "Matrikkelenhet:2",
                ident = matrikkelnummer("9911", 2, 1)
            )
            entity(
                "Matrikkelenhet:3",
                ident = matrikkelnummer("9911", 2, 2)
            )
            entity(
                "Matrikkelenhet:4",
                ident = matrikkelnummer("9911", 3, 1)
            )
            entity(
                "Matrikkelenhet:5",
                ident = matrikkelnummer("9912", 1, 1)
            )
        }

        val transformedFlow = transformer.transform(entitySource.entityFlow)

        transformedFlow.toList()
            .associateBy({ it.id}, { it.transformedIdent })
            .shouldContainExactly(
                mapOf(
                    "Matrikkelenhet:3" to matrikkelnummer("9912", 50, 1),
                )
            )
    }

    test("Flytt et bruksnummer annerledes enn gårdsnummer") {
        val transformer = ParameterbasedTransformer(
            listOf(
                GardsnummerParameter(
                    Kommunenummer(9911), IntRange(2, 2),
                    Kommunenummer(9912), 15
                ),
                BruksnummerParameter(
                    Kommunenummer(9911), Matrikkelnummer.Gardsnummer(2), IntRange(2, 2),
                    Kommunenummer(9913), Matrikkelnummer.Gardsnummer(50), -1
                ),
            )
        )

        val entitySource = mockEntitySource {
            entity(
                "Matrikkelenhet:1",
                ident = matrikkelnummer("9911", 1, 1)
            )
            entity(
                "Matrikkelenhet:2",
                ident = matrikkelnummer("9911", 2, 1)
            )
            entity(
                "Matrikkelenhet:3",
                ident = matrikkelnummer("9911", 2, 2)
            )
            entity(
                "Matrikkelenhet:4",
                ident = matrikkelnummer("9911", 2, 3)
            )
            entity(
                "Matrikkelenhet:5",
                ident = matrikkelnummer("9911", 3, 1)
            )
        }

        val transformedFlow = transformer.transform(entitySource.entityFlow)

        transformedFlow.toList()
            .associateBy({ it.id}, { it.transformedIdent })
            .shouldContainExactly(
                mapOf(
                    "Matrikkelenhet:2" to matrikkelnummer("9912", 17, 1),
                    "Matrikkelenhet:3" to matrikkelnummer("9913", 50, 1),
                    "Matrikkelenhet:4" to matrikkelnummer("9912", 17, 3),
                    // TODO: Reserveringer
                )
            )
    }
})
