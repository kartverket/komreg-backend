package no.kartverket.komreg.transformation

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.transformation.parameters.GardsnummerParameter

class TeigTests : FunSpec({
    xtest("Flytt teig med to matrikkelenheter") {
        val transformer = ParameterbasedTransformer(
            listOf(
                GardsnummerParameter(
                    Kommunenummer(9911), IntRange(1, 2),
                    Kommunenummer(9912), 0
                ),
            )
        )

        val entitySource = mockEntitySource {
            entity(
                "Teig:1",
                associatedIdents = setOf(
                    matrikkelnummer("9911", 1, 1),
                    matrikkelnummer("9911", 1, 2),
                )
            )
        }

        val transformedFlow = transformer.transform(entitySource.entityFlow)

        val single = transformedFlow.toList()
            .shouldHaveSize(1)
            .single()

        withClue(Transformation::id.name) {
            single.id shouldBe "Teig:1"
        }
        withClue(Transformation::transformedAssociatedIdents.name) {
            single.transformedAssociatedIdents
                .shouldNotBeNull()
                .shouldContainOnly(
                    matrikkelnummer("9912", 1, 1),
                    matrikkelnummer("9912", 1, 2),
                )
        }
    }

    xtest("Flytt teig med to matrikkelenheter i ulovlig spagat") {
        val transformer = ParameterbasedTransformer(
            listOf(
                GardsnummerParameter(
                    Kommunenummer(9911), IntRange(1, 1),
                    Kommunenummer(9912), 0
                ),
            )
        )

        val entitySource = mockEntitySource {
            entity(
                "Teig:1",
                associatedIdents = setOf(
                    matrikkelnummer("9911", 1, 1),
                    matrikkelnummer("9911", 1, 2),
                )
            )
        }

        val transformedFlow = transformer.transform(entitySource.entityFlow)

        val single = transformedFlow.toList()
            .shouldHaveSize(1)
            .single()

        withClue(Transformation::id.name) {
            single.id shouldBe "Teig:1"
        }
        withClue(Transformation::transformationType.name) {
            single.transformationType shouldBe "failure"
        }
    }
})
