package no.kartverket.komreg.transformation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import no.kartverket.komreg.core.domain.IdType

enum class TestIdType : IdType<Long, TestIdType> {
    Fylke,
    Kommune,
    Foo;

    override val valueSerializer: KSerializer<Long> = Long.serializer()

    override fun compare(o1: Long, o2: Long): Int {
        return o1.compareTo(o2)
    }
}
