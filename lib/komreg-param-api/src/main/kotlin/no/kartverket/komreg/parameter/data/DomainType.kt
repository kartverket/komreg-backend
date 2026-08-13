package no.kartverket.komreg.parameter.data

import arrow.core.Either
import java.lang.reflect.Modifier
import kotlin.reflect.typeOf


sealed interface DomainType<A : Any> {
    val finalType: FinalType<A>
}

@JvmInline
value class FinalType<A : Any> private constructor(val javaClass: Class<A>) : DomainType<A> {
    companion object {
        inline operator fun  <reified A : Any> invoke(): FinalType<A>  {
            val kType = typeOf<A>()
            require(kType.arguments.all { it.variance == null && it.type == null }) {
                "All arguments to type constructor (class) must be star projected"
            }
            val kClass = A::class
            val javaClass = kClass.java
            return unsafeMake(javaClass)
        }

        fun <A : Any> unsafeMake(javaClass: Class<A>): FinalType<A> {
            val staticModifier = if (javaClass.enclosingClass != null) Modifier.STATIC else 0
            val requiredMods = Modifier.PUBLIC or Modifier.FINAL or staticModifier
            val mods = javaClass.modifiers
            require(mods and requiredMods == requiredMods) {
                "$javaClass must be public and final"
            }
            require(!(javaClass.isHidden || javaClass.isAnonymousClass || javaClass.isLocalClass)) {
                "$javaClass must not be hidden, anonymous or local"
            }
            return FinalType(javaClass)
        }
    }

    override fun toString(): String {
        return "FinalType(${javaClass.name})"
    }

    override val finalType: FinalType<A>
        get() = this
}

interface EnumerableType<A : Any> : DomainType<A> {
    val enumerator: Enumerator<A>
}

interface CombinableType<A : Any> : DomainType<A> {
    fun combine(a: A, b: A): Either<String, A>
}

interface KeyCombinableType<K : Any, V: Any> : DomainType<K> {
    val valueType: CombinableType<V>
}