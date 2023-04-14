package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Ident
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals

object TransformKommunenrTest : Spek({
    describe("A transformation") {
        val reguleringsInput = Reguleringsinput(
            listOf(
                Kommuneendring(
                    fra = Kommunenummer(Fylkesnummer(2), Kommunenummer.Lopenummer(5)),
                    til = Kommunenummer(Fylkesnummer(3), Kommunenummer.Lopenummer(6)),
                ),
            ),
        )

        describe("of idents") {
            it("should change kommunenr based on input") {
                val entity = Entity(dummyId(123), identOf(2, 5))
                val result = transformerKommunenummer(reguleringsInput, entity)
                val expected = identOf(3, 6)
                assertEquals(expected, result?.transformedIdent)
            }
            it("should not transform entity when unmatched idents") {
                val entity = Entity(dummyId(123), identOf(10, 50))
                val result = transformerKommunenummer(reguleringsInput, entity)
                assertEquals(null, result)
            }
        }

        describe("of associated idents") {
            it("should change all matching idents") {
                val entity = Entity(
                    dummyId(123),
                    associatedIdents = setOf(
                        identOf(2, 5, 1),
                        identOf(2, 5, 2),
                    ),
                )
                val result = transformerKommunenummer(reguleringsInput, entity)
                val expected = setOf(
                    identOf(3, 6, 1),
                    identOf(3, 6, 2),
                )
                assertEquals(expected, result?.transformedAssociatedIdents)
            }
            it("should not transform entity when unmatched idents") {
                val entity = Entity(
                    dummyId(123),
                    associatedIdents = setOf(
                        identOf(10, 5, 1),
                        identOf(10, 5, 2),
                    ),
                )
                val result = transformerKommunenummer(reguleringsInput, entity)
                val expected = null
                assertEquals(expected, result)
            }
            it("should only change matching idents") {
                val entity = Entity(
                    dummyId(123),
                    associatedIdents = setOf(
                        identOf(2, 5, 1),
                        identOf(10, 15, 1),
                    ),
                )
                val result = transformerKommunenummer(reguleringsInput, entity)
                val expected = setOf(
                    identOf(3, 6, 1),
                    identOf(10, 15, 1),
                )
                assertEquals(expected, result?.transformedAssociatedIdents)
            }
        }
    }
})

private fun identOf(fylkesnummer: Int, lopenummer: Int, gardsnummer: Int? = null) =
    if (gardsnummer != null) {
        Ident(
            Fylkesnummer(fylkesnummer.toLong()),
            Kommunenummer.Lopenummer(lopenummer.toByte()),
            Matrikkelnummer.Gardsnummer(gardsnummer),
        )
    } else {
        Ident(
            Fylkesnummer(fylkesnummer.toLong()),
            Kommunenummer.Lopenummer(lopenummer.toByte()),
        )
    }
