package no.kartverket.komreg.core.spi

import no.kartverket.komreg.core.And
import kotlin.reflect.KClass

interface Normalizer<A : Any> {
    val type: KClass<A>
    fun normalize(a: A?): And<*, *>
}
