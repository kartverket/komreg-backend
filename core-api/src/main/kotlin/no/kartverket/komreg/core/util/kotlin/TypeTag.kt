package no.kartverket.komreg.core.util.kotlin

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf

@Suppress("UNCHECKED_CAST")
@JvmInline
value class TypeTag<A> private constructor(val type: KType) {

    companion object {
        operator fun  invoke(type: KType): TypeTag<*> {
            return TypeTag<Nothing>(type)
        }

        @Suppress("NOTHING_TO_INLINE")
        inline operator fun  <A : Any> invoke(type: KClass<A>): TypeTag<A> {
            return invoke(type.starProjectedType) as TypeTag<A>
        }

        inline operator fun <reified A> invoke(): TypeTag<A> {
            @Suppress("UNCHECKED_CAST")
            return invoke(typeOf<A>()) as TypeTag<A>
        }
    }

    override fun toString(): String {
        return "TypeTag<${type}>"
    }
}