package no.kartverket.komreg.transformation.reform

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Bygningsnummer
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.Payload
import no.kartverket.komreg.transformation.dummyKommune
import no.kartverket.komreg.transformation.tilKommunedata

class ReformOpSerializationTest : FunSpec({
    val json = Json {
        serializersModule = SerializersModule {
            polymorphic(Payload::class) {
                subclass(Kommunedata::class)
                subclass(Fylkesdata::class)
            }
            polymorphic(Comparable::class) {
                subclass(Fylkesnummer::class, Fylkesnummer.serializer())
                subclass(Kommunenummer.Lopenummer::class, Kommunenummer.Lopenummer.serializer())
                subclass(Matrikkelnummer.Gardsnummer::class, Matrikkelnummer.Gardsnummer.serializer())
                subclass(Matrikkelnummer.Bruksnummer::class, Matrikkelnummer.Bruksnummer.serializer())
                subclass(Bygningsnummer::class, Bygningsnummer.serializer())
                subclass(Adressekode::class, Adressekode.serializer())
            }
        }
    }
    val dato = LocalDate(2027, 1, 1)

    test("alle operasjonene tåler en JSON-rundtur, og identtypene kommer fra cachen") {
        val ops = reform {
            create(kommune(5, 3), dummyKommune(5, 3).tilKommunedata(dato))
            retireTo(kommune(5, 1), kommune(5, 3))
            retire(fylke(31))
            retire(kommune(5, 5), successor = kommune(5, 7))
            derive(kommune(5, 7), from = kommune(5, 5), payload = dummyKommune(5, 7).tilKommunedata(dato))
            derive(veg(5, 3, 200), from = veg(5, 1, 100))
            move(grunneiendom(5, 1, 1, 1), grunneiendom(5, 3, 101, 1))
        }
        val regulering = ReformRegulering("test", dato, ops)

        val text = json.encodeToString(ReformRegulering.serializer(), regulering)
        val decoded = json.decodeFromString(ReformRegulering.serializer(), text)

        text shouldContain "\"type\":\"retireTo\""
        decoded shouldBe regulering
        (decoded.regler[6] as ReformOp.Move).source.type shouldBeSameInstanceAs grunneiendom(5, 1, 1, 1).type
        ReformClaims.validate(decoded.regler).isRight() shouldBe true
    }

    test("tom ident avvises") {
        val text = """[{"type":"retire","source":{"type":[],"values":[]}}]"""
        shouldThrow<SerializationException> {
            json.decodeFromString(ListSerializer(ReformOp.serializer()), text)
        }
    }
})
