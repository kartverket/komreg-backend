package no.kartverket.komreg.transformation.reform

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.Payload
import no.kartverket.komreg.integration.spi.identTypeOf3
import no.kartverket.komreg.integration.spi.identTypeOf4
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.transformation.getKommuneIdentType
import no.kartverket.komreg.transformation.getMatrikkelenhetIdentType
import no.kartverket.komreg.transformation.reform.ReformOp.Create
import no.kartverket.komreg.transformation.reform.ReformOp.Derive
import no.kartverket.komreg.transformation.reform.ReformOp.Move
import no.kartverket.komreg.transformation.reform.ReformOp.Retire
import no.kartverket.komreg.transformation.reform.ReformOp.RetireTo

class ReformClaimsTest : FunSpec({
    val payload = object : Payload {}

    suspend fun kommune(fnr: Long, lnr: Byte) = getKommuneIdentType()(Fylkesnummer(fnr), Kommunenummer.Lopenummer(lnr))

    suspend fun gnr(fnr: Long, lnr: Byte, gnr: Int) =
        identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Matrikkelnummer.Gardsnummer>()(
            Fylkesnummer(fnr), Kommunenummer.Lopenummer(lnr), Matrikkelnummer.Gardsnummer(gnr),
        )

    suspend fun grunneiendom(fnr: Long, lnr: Byte, gnr: Int, bnr: Short) =
        identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Matrikkelnummer.Gardsnummer, Matrikkelnummer.Bruksnummer>()(
            Fylkesnummer(fnr), Kommunenummer.Lopenummer(lnr), Matrikkelnummer.Gardsnummer(gnr), Matrikkelnummer.Bruksnummer(bnr),
        )

    suspend fun matrikkelenhet(fnr: Long, lnr: Byte, gnr: Int, bnr: Short) =
        getMatrikkelenhetIdentType()(
            Fylkesnummer(fnr), Kommunenummer.Lopenummer(lnr), Matrikkelnummer.Gardsnummer(gnr),
            Matrikkelnummer.Bruksnummer(bnr), Matrikkelnummer.Festenummer(0), Matrikkelnummer.Seksjonsnummer(0),
        )

    context("gyldige sett") {
        test("kommunesammenslåing") {
            val k1 = kommune(3, 1)
            val k2 = kommune(3, 2)
            val k3 = kommune(3, 3)
            val ops = reform {
                create(k3, payload)
                retireTo(k1, k3)
                retireTo(k2, k3)
            }

            val claims = ReformClaims.validate(ops).shouldBeRight()

            claims.creates shouldContainExactly listOf(Create(k3, payload))
            claims.requiredExistingTargets.shouldBeEmpty()
            claims.lookup(matrikkelenhet(3, 1, 1, 1)) shouldBe ReformMatch(RetireTo(k1, k3), 2, 4)
        }

        test("sammenslåing inn i eksisterende kommune krever at målet finnes") {
            val k1 = kommune(3, 1)
            val k2 = kommune(3, 2)

            val claims = ReformClaims.validate(reform { retireTo(k1, k2) }).shouldBeRight()

            claims.requiredExistingTargets shouldBe setOf(k2)
        }

        test("kommunedeling") {
            val k1 = kommune(3, 1)
            val k2 = kommune(3, 2)
            val k3 = kommune(3, 3)
            val ops = reform {
                retire(k1, successor = k2)
                derive(k2, from = k1, payload)
                derive(k3, from = k1, payload)
                move(gnr(3, 1, 1), gnr(3, 2, 1))
                move(gnr(3, 1, 2), gnr(3, 3, 1))
            }

            val claims = ReformClaims.validate(ops).shouldBeRight()

            claims.creates.shouldBeEmpty()
            claims.derives shouldBe mapOf(k1 to listOf(Derive(k2, k1, payload), Derive(k3, k1, payload)))
            claims.requiredExistingTargets.shouldBeEmpty()
            claims.lookup(matrikkelenhet(3, 1, 2, 5)) shouldBe ReformMatch(Move(gnr(3, 1, 2), gnr(3, 3, 1)), 3, 3)
            claims.lookup(matrikkelenhet(3, 1, 9, 5)) shouldBe ReformMatch(Retire(k1, k2), 2, 4)
            claims.lookup(k1) shouldBe ReformMatch(Retire(k1, k2), 2, 0)
            claims.lookup(k1)!!.isExact shouldBe true
        }

        test("etterfølger som ikke opprettes, må finnes fra før") {
            val k1 = kommune(3, 1)
            val k2 = kommune(3, 2)

            val claims = ReformClaims.validate(reform { retire(k1, successor = k2) }).shouldBeRight()

            claims.requiredExistingTargets shouldBe setOf(k2)
        }

        test("Derive kan være fra et svakt mål, fra en kilde og flere ganger fra samme nøkkel") {
            val k1 = kommune(3, 1)
            val k2 = kommune(3, 2)
            val ops = reform {
                retireTo(k1, k2)
                derive(kommune(3, 3), from = k2)
                derive(kommune(3, 4), from = k1)
                derive(kommune(3, 5), from = k1)
            }

            val claims = ReformClaims.validate(ops).shouldBeRight()

            claims.derives shouldBe mapOf(
                k2 to listOf(Derive(kommune(3, 3), k2)),
                k1 to listOf(Derive(kommune(3, 4), k1), Derive(kommune(3, 5), k1)),
            )
            claims.requiredExistingTargets shouldBe setOf(k2)
        }

        test("gårdsnummerflytt med bruksnummerunntak") {
            val ops = reform {
                move(gnr(3, 1, 40), gnr(3, 1, 60))
                move(grunneiendom(3, 1, 40, 5), grunneiendom(3, 1, 61, 1))
            }

            val claims = ReformClaims.validate(ops).shouldBeRight()

            claims.lookup(matrikkelenhet(3, 1, 40, 5))!!.operation shouldBe
                Move(grunneiendom(3, 1, 40, 5), grunneiendom(3, 1, 61, 1))
            claims.lookup(matrikkelenhet(3, 1, 40, 6))!!.operation shouldBe Move(gnr(3, 1, 40), gnr(3, 1, 60))
        }

        test("identiske duplikater regnes som én regel") {
            val k1 = kommune(3, 1)
            val k2 = kommune(3, 2)

            val claims = ReformClaims.validate(reform { move(k1, k2); move(k1, k2) }).shouldBeRight()

            claims.declarations shouldContainExactly listOf(Move(k1, k2))
        }
    }

    context("lookup") {
        test("mest spesifikke regel vinner") {
            val ops = reform {
                move(kommune(3, 1), kommune(3, 2))
                move(gnr(3, 1, 40), gnr(3, 2, 60))
            }

            val claims = ReformClaims.validate(ops).shouldBeRight()

            claims.lookup(grunneiendom(3, 1, 40, 1)) shouldBe ReformMatch(Move(gnr(3, 1, 40), gnr(3, 2, 60)), 3, 1)
            claims.lookup(grunneiendom(3, 1, 41, 1)) shouldBe ReformMatch(Move(kommune(3, 1), kommune(3, 2)), 2, 2)
        }

        test("ingen treff gir null") {
            val claims = ReformClaims.validate(reform { move(kommune(3, 1), kommune(3, 2)) }).shouldBeRight()

            claims.lookup(grunneiendom(3, 5, 40, 1)) shouldBe null
        }
    }

    context("feil") {
        test("Diverges") {
            val k1 = kommune(3, 1)
            val ops = reform {
                move(k1, kommune(3, 2))
                retire(k1)
            }

            ReformClaims.validate(ops).shouldBeLeft() shouldContainExactly listOf(
                ReformErr.Diverges(k1, listOf(Move(k1, kommune(3, 2)), Retire(k1))),
            )
        }

        test("Conflict") {
            val k3 = kommune(3, 3)
            val ops = reform {
                move(kommune(3, 1), k3)
                create(k3, payload)
                derive(k3, from = kommune(3, 2))
            }

            ReformClaims.validate(ops).shouldBeLeft() shouldContainExactly listOf(
                ReformErr.Conflict(k3, listOf(Move(kommune(3, 1), k3), Create(k3, payload), Derive(k3, kommune(3, 2)))),
            )
        }

        test("SourceIsTarget for eksklusivt og svakt mål, uten TargetUnderSource") {
            val k1 = kommune(3, 1)
            val k2 = kommune(3, 2)
            val k3 = kommune(3, 3)
            val ops = reform {
                move(k1, k2)
                retireTo(k2, k3)
                retire(k3)
            }

            ReformClaims.validate(ops).shouldBeLeft() shouldContainExactlyInAnyOrder listOf(
                ReformErr.SourceIsTarget(k2, listOf(RetireTo(k2, k3)), listOf(Move(k1, k2))),
                ReformErr.SourceIsTarget(k3, listOf(Retire(k3)), listOf(RetireTo(k2, k3))),
            )
        }

        test("SourceIsTarget for etterfølger") {
            val k1 = kommune(3, 1)
            val k2 = kommune(3, 2)
            val ops = reform {
                retire(k1, successor = k2)
                retire(k2)
            }

            ReformClaims.validate(ops).shouldBeLeft() shouldContainExactly listOf(
                ReformErr.SourceIsTarget(k2, listOf(Retire(k2)), listOf(Retire(k1, k2))),
            )
        }

        test("TargetUnderSource for Move-kilde") {
            val k1 = kommune(3, 1)
            val target = gnr(3, 1, 6)
            val ops = reform {
                move(k1, kommune(3, 2))
                move(gnr(3, 3, 1), target)
            }

            ReformClaims.validate(ops).shouldBeLeft() shouldContainExactly listOf(
                ReformErr.TargetUnderSource(target, Move(gnr(3, 3, 1), target), k1, listOf(Move(k1, kommune(3, 2)))),
            )
        }

        test("TargetUnderSource for svakt mål") {
            val k1 = kommune(3, 1)
            val target = gnr(3, 1, 6)
            val ops = reform {
                retire(k1)
                retireTo(gnr(3, 3, 1), target)
            }

            ReformClaims.validate(ops).shouldBeLeft() shouldContainExactly listOf(
                ReformErr.TargetUnderSource(target, RetireTo(gnr(3, 3, 1), target), k1, listOf(Retire(k1))),
            )
        }

        test("TargetUnderSource for etterfølger") {
            val k1 = kommune(3, 1)
            val target = gnr(3, 1, 6)
            val ops = reform {
                retire(k1)
                retire(gnr(3, 3, 1), successor = target)
            }

            ReformClaims.validate(ops).shouldBeLeft() shouldContainExactly listOf(
                ReformErr.TargetUnderSource(target, Retire(gnr(3, 3, 1), target), k1, listOf(Retire(k1))),
            )
        }

        test("SourceUnderCreatedTarget") {
            val k2 = kommune(3, 2)
            val k4 = kommune(3, 4)
            val ops = reform {
                create(k2, payload)
                derive(k4, from = kommune(3, 3))
                move(gnr(3, 2, 1), gnr(3, 5, 1))
                retire(gnr(3, 4, 1))
            }

            ReformClaims.validate(ops).shouldBeLeft() shouldContainExactly listOf(
                ReformErr.SourceUnderCreatedTarget(gnr(3, 2, 1), Move(gnr(3, 2, 1), gnr(3, 5, 1)), k2, Create(k2, payload)),
                ReformErr.SourceUnderCreatedTarget(gnr(3, 4, 1), Retire(gnr(3, 4, 1)), k4, Derive(k4, kommune(3, 3))),
            )
        }

        test("SourceUnderCreatedTarget for Derive.from") {
            val k2 = kommune(3, 2)
            val derive = Derive(gnr(3, 6, 1), from = gnr(3, 2, 1))
            val ops = listOf(Create(k2, payload), derive)

            ReformClaims.validate(ops).shouldBeLeft() shouldContainExactly listOf(
                ReformErr.SourceUnderCreatedTarget(gnr(3, 2, 1), derive, k2, Create(k2, payload)),
            )
        }

        test("DeriveFromTarget for eget og andres mål") {
            val k1 = kommune(3, 1)
            val k2 = kommune(3, 2)
            val own = Derive(k1, from = k1)
            val fromMoved = Derive(kommune(3, 3), from = k2)
            val ops = listOf(own, Move(kommune(3, 4), k2), fromMoved)

            ReformClaims.validate(ops).shouldBeLeft() shouldContainExactly listOf(
                ReformErr.DeriveFromTarget(own, listOf(own)),
                ReformErr.DeriveFromTarget(fromMoved, listOf(Move(kommune(3, 4), k2))),
            )
        }

        test("DeriveTypeMismatch") {
            val derive = Derive(gnr(3, 2, 1), from = kommune(3, 1))

            ReformClaims.validate(listOf(derive)).shouldBeLeft() shouldContainExactly listOf(
                ReformErr.DeriveTypeMismatch(derive),
            )
        }

        test("flere feil rapporteres samtidig") {
            val k1 = kommune(3, 1)
            val k2 = kommune(3, 2)
            val ops = reform {
                move(k1, k2)
                retire(k1)
                create(k2, payload)
                move(gnr(3, 2, 1), gnr(3, 6, 1))
            }

            val errors = ReformClaims.validate(ops).shouldBeLeft()

            errors.map { it::class } shouldContainExactlyInAnyOrder listOf(
                ReformErr.Diverges::class,
                ReformErr.Conflict::class,
                ReformErr.SourceUnderCreatedTarget::class,
            )
            errors.forEach { it.message.isNotBlank() shouldBe true }
        }
    }
})
