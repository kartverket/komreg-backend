package no.kartverket.komreg.integration.spi

import java.util.stream.Collectors
import kotlin.reflect.KClass
import kotlin.streams.asStream

class Ident private constructor(val map: Map<KClass<*>, *>) {
    constructor(vararg values: Any) : this(
        values.asSequence()
            .asStream()
            .collect(
                Collectors.toMap(
                    { it::class },
                    { it },
                ),
            ),
    )

    inline fun <reified T> get(): T = map[T::class] as T

    fun transform(transformation: Ident): Ident {
        if (!map.keys.containsAll(transformation.map.keys)) {
            val extra = transformation.map.keys - map.keys
            throw IllegalArgumentException("Can not transform non-existing values: $extra")
        }

        return Ident(map.plus(transformation.map))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Ident) return false

        if (map != other.map) return false

        return true
    }

    override fun hashCode(): Int {
        return map.hashCode()
    }

    override fun toString(): String {
        return "Ident(map=$map)"
    }
}
