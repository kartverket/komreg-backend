package no.kartverket.komreg.transformation

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.getOrThrow
import java.util.concurrent.atomic.AtomicLong

fun mockIdGenerator(startAt: Long = 1) = mockk<IdGeneratorManager> {
    val nextId = AtomicLong(startAt)
    coEvery { idFor(TestIdType.Kommune, any()) }.answers { call ->
        val ident = call.invocation.args[1] as Ident
        val fylkesnummer = ident.getOrThrow<Fylkesnummer>()
        val lopenummer = ident.getOrThrow<Kommunenummer.Lopenummer>()
        val idValue = fylkesnummer.value * 100 + lopenummer.value
        Id(TestIdType.Kommune, idValue)
    }
    coEvery { idFor(TestIdType.Foo, any()) }.answers { Id(TestIdType.Foo, nextId.getAndIncrement()) }
}
