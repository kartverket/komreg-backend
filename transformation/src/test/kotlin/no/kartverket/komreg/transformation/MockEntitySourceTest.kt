package no.kartverket.komreg.transformation

import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forOne
import kotlinx.coroutines.flow.toList
import no.kartverket.komreg.core.domain.Matrikkelnummer

class MockEntitySourceTest : FunSpec({
    test("Opprettelse av matrikkelenhetflow") {
        val entitySource = mockEntitySource {
            entity(
                "Matrikkelenhet:1234",
                ident = matrikkelnummer("9876", 170, 1)
            )
            entity(
                "Matrikkelenhet:5678",
                ident = matrikkelnummer("9876", 4, 2)
            )
        }

        with(entitySource.entityFlow.toList()) {
            forOne { entity ->
                entity shouldHaveId "Matrikkelenhet:1234"
                entity.shouldHaveIdent(
                    Matrikkelnummer.Gardsnummer(170),
                    Matrikkelnummer.Bruksnummer(1),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                )
            }
            forOne { entity ->
                entity shouldHaveId "Matrikkelenhet:5678"
                entity.shouldHaveIdent(
                    Matrikkelnummer.Gardsnummer(4),
                    Matrikkelnummer.Bruksnummer(2),
                    Matrikkelnummer.Festenummer(0),
                    Matrikkelnummer.Seksjonsnummer(0),
                )
            }
        }
    }
})
