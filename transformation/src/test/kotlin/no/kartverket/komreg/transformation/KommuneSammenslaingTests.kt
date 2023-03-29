package no.kartverket.komreg.transformation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import kotlinx.coroutines.flow.toList
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.transformation.parameters.GardsnummerParameter
import no.kartverket.komreg.transformation.parameters.NyKommuneParameter
import no.kartverket.komreg.transformation.parameters.UtgaendeKommuneParameter

class KommuneSammenslaingTests : FunSpec({
    test("To til en, ingen gårdsnummerkonflikt") {
        val transformer = ParameterbasedTransformer(
            listOf(
                NyKommuneParameter(
                    Kommunenummer(9902), "Ny kommune"
                ),
                UtgaendeKommuneParameter(
                    Kommunenummer(9901),
                    Kommunenummer(9902)
                ),
                UtgaendeKommuneParameter(
                    Kommunenummer(9911),
                    Kommunenummer(9902)
                ),
            )
        )

        val entitySource = mockEntitySource {
            entity(
                "Matrikkelenhet:1",
                ident = matrikkelnummer("9901", 1, 1)
            )
            entity(
                "Matrikkelenhet:2",
                ident = matrikkelnummer("9901", 1, 2)
            )
            entity(
                "Matrikkelenhet:3",
                ident = matrikkelnummer("9903", 1, 1)
            )
            entity(
                "Matrikkelenhet:4",
                ident = matrikkelnummer("9911", 11, 1)
            )
            entity(
                "Matrikkelenhet:5",
                ident = matrikkelnummer("9801", 1, 1)
            )
        }

        val transformedFlow = transformer.transform(entitySource.entityFlow)

        transformedFlow.toList()
            .associateBy({ it.id}, { it.transformedIdent })
            .shouldContainExactly(
                mapOf(
                    "Kommune:9902" to kommunenummer("9902"),
                    "Matrikkelenhet:1" to matrikkelnummer("9902", 1, 1),
                    "Matrikkelenhet:2" to matrikkelnummer("9902", 1, 2),
                    "Matrikkelenhet:4" to matrikkelnummer("9902", 11, 1),
                    // TODO: Sette 9901 og 9911 utgått
                )
            )
    }

    test("To til en, med gårdsnummerforskyving") {
        val transformer = ParameterbasedTransformer(
            listOf(
                NyKommuneParameter(
                    Kommunenummer(9902), "Ny kommune"
                ),
                UtgaendeKommuneParameter(
                    Kommunenummer(9901),
                    Kommunenummer(9902)
                ),
                UtgaendeKommuneParameter(
                    Kommunenummer(9911),
                    Kommunenummer(9902)
                ),
                GardsnummerParameter(
                    Kommunenummer(9911), IntRange(1, 99999),
                    Kommunenummer(9902), 10
                )
            )
        )

        val entitySource = mockEntitySource {
            entity(
                "Matrikkelenhet:1",
                ident = matrikkelnummer("9901", 1, 1)
            )
            entity(
                "Matrikkelenhet:2",
                ident = matrikkelnummer("9901", 1, 2)
            )
            entity(
                "Matrikkelenhet:3",
                ident = matrikkelnummer("9903", 1, 1)
            )
            entity(
                "Matrikkelenhet:4",
                ident = matrikkelnummer("9911", 1, 1)
            )
            entity(
                "Matrikkelenhet:5",
                ident = matrikkelnummer("9801", 1, 1)
            )
        }

        val transformedFlow = transformer.transform(entitySource.entityFlow)

        transformedFlow.toList()
            .associateBy({ it.id}, { it.transformedIdent })
            .shouldContainExactly(
                mapOf(
                    "Kommune:9902" to kommunenummer("9902"),
                    "Matrikkelenhet:1" to matrikkelnummer("9902", 1, 1),
                    "Matrikkelenhet:2" to matrikkelnummer("9902", 1, 2),
                    "Matrikkelenhet:4" to matrikkelnummer("9902", 11, 1),
                    // TODO: Sette 9901 og 9911 utgått
                )
            )
    }
})
