package no.kartverket.komreg.transformation.reform

import arrow.core.getOrElse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.EntityProcessor
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.transformation.MockSink
import no.kartverket.komreg.transformation.TestIdType
import no.kartverket.komreg.transformation.TestStorage
import no.kartverket.komreg.transformation.dummyKommune
import no.kartverket.komreg.transformation.mockIdGenerator
import no.kartverket.komreg.transformation.mockSource
import no.kartverket.komreg.transformation.tilKommunedata
import no.kartverket.komreg.transformation.transform

private class RecordingProcessor : EntityProcessor {
    val consumed = mutableListOf<Transformation>()
    override fun consume(t: Transformation) {
        consumed += t
    }

    override fun produce(): Flow<Transformation> = emptyFlow()
}

/** `transform()` med `ReformClaims` (motor `reform`) mot `MockSource`/`MockSink`. */
class TransformReformTest : FunSpec({
    val dato = LocalDate(2027, 1, 1)

    test("sammenslåing til ny kommune: Create skrives før processorene og tilbakeføres i første pass") {
        val kommunedata = dummyKommune(5, 3).tilKommunedata(dato)
        val claims = ReformClaims.validate(
            reform {
                create(kommune(5, 3), kommunedata)
                retireTo(kommune(5, 1), kommune(5, 3))
                retireTo(kommune(5, 2), kommune(5, 3))
            },
        ).getOrElse { error(it) }

        val kommune1 = Entity(Id(TestIdType.Kommune, 501), kommune(5, 1))
        val kommune2 = Entity(Id(TestIdType.Kommune, 502), kommune(5, 2))
        val bygning = Entity(Id(TestIdType.Foo, 1), bygning(5, 2, 1), setOf(kommune(5, 2)))
        val uendret = Entity(Id(TestIdType.Foo, 2), bygning(9, 9, 1))

        val processor = RecordingProcessor()
        val sink = MockSink()

        transform(
            kjoringId = 1,
            claims = claims,
            ikrafttredelsesdato = dato,
            idTypeFor = ::testIdTypeFor,
            lifeCycleHandlers = emptyList(),
            entitySources = listOf(mockSource(kommune1, kommune2, bygning, uendret)),
            entityProcessors = listOf(processor),
            entitySinks = listOf(sink),
            idGeneratorManager = mockIdGenerator(),
            storage = TestStorage(),
            skalTilbakefores = true,
        )

        val opprettet = Row(Id(TestIdType.Kommune, 503), kommune(5, 3), null, kommunedata)
        val erstattet = listOf(
            Row(kommune1.id, kommune(5, 3), null, null),
            Row(kommune2.id, kommune(5, 3), null, null),
            Row(bygning.id, bygning(5, 3, 1), setOf(kommune(5, 3)), null),
        )

        processor.consumed.map { it.row() } shouldContainExactlyInAnyOrder listOf(opprettet) + erstattet
        processor.consumed.single { it.id == opprettet.id }.sourceEntity shouldBe null

        // MockSink får først nyopprettinger, så erstattende entiteter.
        sink.transformations.map { it.row() } shouldBe listOf(opprettet) + erstattet
    }
})
