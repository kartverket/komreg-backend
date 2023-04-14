package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType

fun dummyId(value: Int) : Id<*> {
    return Id(DummyId, value)
}

object DummyId : IdType<Int, DummyId> {
    override fun compareTo(other: DummyId): Int {
        return 0
    }

    override fun compare(o1: Int, o2: Int): Int {
        return o1.compareTo(o2)
    }
}
