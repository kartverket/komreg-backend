package no.kartverket.komreg.repositories

import no.kartverket.komreg.core.KjoringContext
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType
import no.kartverket.komreg.integration.spi.IdGenerator
import no.kartverket.komreg.integration.spi.IdGeneratorFactory
import java.util.concurrent.atomic.AtomicLong

class FooIdGeneratorFactory : IdGeneratorFactory {
    override fun createFor(context: KjoringContext, idType: IdType<*, *>): IdGenerator? {
        return if (idType == TestIdType.Foo) {
            idGenerator
        } else {
            null
        }
    }

    companion object {
        val idGenerator = object : IdGenerator {
            private val longRef = AtomicLong(0)
            override fun generateId(hint: Any?): Id {
                return Id(TestIdType.Foo, longRef.getAndIncrement())
            }
        }
    }
}

