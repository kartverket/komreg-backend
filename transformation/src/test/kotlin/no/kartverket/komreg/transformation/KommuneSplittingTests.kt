package no.kartverket.komreg.transformation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import kotlinx.coroutines.flow.toList
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.transformation.parameters.GardsnummerParameter
import no.kartverket.komreg.transformation.parameters.NyKommuneParameter
import no.kartverket.komreg.transformation.parameters.UtgaendeKommuneParameter

class KommuneSplittingTests : FunSpec({
    test("Del i to") {
        val transformer = ParameterbasedTransformer(
            listOf(
                NyKommuneParameter(
                    Kommunenummer(9912), "Ny kommune 1"
                ),
                NyKommuneParameter(
                    Kommunenummer(9913), "Ny kommune 2"
                ),
                UtgaendeKommuneParameter(
                    Kommunenummer(9911),
                    Kommunenummer(9912)
                ),
                GardsnummerParameter(
                    Kommunenummer(9911), IntRange(1, 1),
                    Kommunenummer(9912), 0
                ),
                GardsnummerParameter(
                    Kommunenummer(9911), IntRange(2, 3),
                    Kommunenummer(9913), 0
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
                ident = matrikkelnummer("9911", 1, 2)
            )
            entity(
                "Matrikkelenhet:3",
                ident = matrikkelnummer("9911", 2, 1)
            )
            entity(
                "Matrikkelenhet:4",
                ident = matrikkelnummer("9911", 3, 1)
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
                    "Kommune:9912" to kommunenummer("9912"),
                    "Kommune:9913" to kommunenummer("9913"),
                    "Matrikkelenhet:1" to matrikkelnummer("9912", 1, 1),
                    "Matrikkelenhet:2" to matrikkelnummer("9912", 1, 2),
                    "Matrikkelenhet:3" to matrikkelnummer("9913", 2, 1),
                    "Matrikkelenhet:4" to matrikkelnummer("9913", 3, 1),
                    // TODO: Sette 9911 utgått
                )
            )
    }
})
