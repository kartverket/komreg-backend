package no.kartverket.komreg.transformation.reform

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Bruksnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Festenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Seksjonsnummer
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.identTypeOf4

/**
 * Låser beslutning 3 i `docs/handoff-1-generator.md`: `IdentType`-cachen gir samme instans uansett hvordan
 * identen er laget, slik at nøkler kan brukes direkte i `HashMap` og i [ReformClaims.lookup].
 */
class IdentTypeInvariansTest : FunSpec({
    val json = Json {
        serializersModule = SerializersModule {
            polymorphic(Comparable::class) {
                subclass(Fylkesnummer::class)
                subclass(Kommunenummer.Lopenummer::class)
                subclass(Gardsnummer::class)
                subclass(Bruksnummer::class)
                subclass(Festenummer::class)
                subclass(Seksjonsnummer::class)
            }
        }
    }

    suspend fun direkte() = grunneiendom(10, 20, 40, 1)
    suspend fun fraDropLast() = matrikkelenhet(10, 20, 40, 1).dropLast().dropLast()

    test("dropLast gir samme type-instans som identTypeOf4, og lik ident") {
        val direkte = direkte()
        val fraDropLast = fraDropLast()

        fraDropLast.type shouldBeSameInstanceAs direkte.type
        fraDropLast.type shouldBeSameInstanceAs identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Gardsnummer, Bruksnummer>()
        fraDropLast shouldBe direkte
        fraDropLast.hashCode() shouldBe direkte.hashCode()
    }

    test("det samme holder etter en JSON-rundtur") {
        val direkte = direkte()
        val seksKomponenter = matrikkelenhet(10, 20, 40, 1)
        val rundtur = json.decodeFromString<Ident>(json.encodeToString<Ident>(seksKomponenter))

        rundtur.type shouldBeSameInstanceAs seksKomponenter.type
        rundtur shouldBe seksKomponenter

        val fraRundtur = (rundtur as Ident.NonEmpty).dropLast().let { (it as Ident.NonEmpty).dropLast() }
        fraRundtur.type shouldBeSameInstanceAs direkte.type
        fraRundtur shouldBe direkte
        fraRundtur.hashCode() shouldBe direkte.hashCode()

        val direkteRundtur = json.decodeFromString<Ident>(json.encodeToString<Ident>(direkte))
        direkteRundtur.type shouldBeSameInstanceAs direkte.type
        direkteRundtur.hashCode() shouldBe direkte.hashCode()
    }

    test("nøkkel fra dropLast finner regel i HashMap og i ReformClaims.lookup") {
        val regler = hashMapOf<Ident, String>(direkte() to "regel")
        regler[fraDropLast()] shouldBe "regel"

        val claims = claimsOf(reform { move(direkte(), grunneiendom(10, 20, 41, 1)) })
        val rundtur = json.decodeFromString<Ident>(json.encodeToString<Ident>(matrikkelenhet(10, 20, 40, 1)))

        claims.lookup(fraDropLast() as Ident.NonEmpty).shouldNotBeNull().matchedSize shouldBe 4
        claims.lookup(rundtur as Ident.NonEmpty).shouldNotBeNull().matchedSize shouldBe 4
    }
})
