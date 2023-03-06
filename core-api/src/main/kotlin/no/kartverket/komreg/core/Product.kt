package no.kartverket.komreg.core

import arrow.core.*
import no.kartverket.komreg.core.Product.Just
import no.kartverket.komreg.core.Product.Mirror.Companion.mirrorOf
import no.kartverket.komreg.core.spi.Unsafe
import kotlin.reflect.KType
import kotlin.reflect.typeOf

sealed class Product<out A : Product<A>> {
    class Just<out A>(override val elems: Nel<Pair<KType, Any?>>) : Product<Just<A>>()
    class And<out A, out B>(override val elems: Nel<Pair<KType, Any?>>) : Product<And<A, B>>()

    class Mirror<@Suppress("unused")
        out A : Product<*>,> private constructor(
        val types: List<KType>,
    ) {
        companion object {
            inline fun <reified A> length1(): Mirror<Just<A>> = Unsafe.mirrorOf(typeOf<A>())
            inline fun <reified A, reified B> length2(): Mirror<And<A, B>> = Unsafe.mirrorOf(typeOf<A>(), typeOf<B>())
            inline fun <reified A, reified B, reified C> length3(): Mirror<And<And<A, B>, C>> =
                Unsafe.mirrorOf(typeOf<A>(), typeOf<B>(), typeOf<C>())

            inline fun <reified A, reified B, reified C, reified D> length4(): Mirror<And<And<And<A, B>, C>, D>> =
                Unsafe.mirrorOf(typeOf<A>(), typeOf<B>(), typeOf<C>(), typeOf<D>())

            inline fun <reified A, reified B, reified C, reified D, reified E> length5(): Mirror<And<And<And<And<A, B>, C>, D>, E>> =
                Unsafe.mirrorOf(typeOf<A>(), typeOf<B>(), typeOf<C>(), typeOf<D>(), typeOf<E>())

            @Suppress("UnusedReceiverParameter")
            fun <A : Product<*>> Unsafe.mirrorOf(vararg types: KType): Mirror<A> = Mirror(types.toList())
        }
    }

    abstract val elems: Nel<Pair<KType, Any?>>
    val mirror: Mirror<Product<A>> by lazy { Unsafe.mirrorOf(*elems.map { it.first }.toTypedArray()) }

    override fun toString(): String {
        return elems.map { it.second }.joinToString(prefix = "(", postfix = ")")
    }
}

typealias And<A, B> = Product.And<A, B>

inline val <reified A> A.product: Just<A> get() = Just(Pair(typeOf<A>(), this).nel())
inline infix fun <reified A, reified B> A.productAnd(b: B): And<A, B> =
    Product.And(nonEmptyListOf(typeOf<A>() to this, typeOf<B>() to b))

inline infix fun <reified A, reified B> Just<A>.and(b: B): And<A, B> = Product.And(this.elems + (typeOf<B>() to b))
inline infix fun <reified A : And<*, *>, reified B> A.and(b: B): And<A, B> = Product.And(this.elems + (typeOf<B>() to b))

// operator fun <A, R> ((A) -> R).invoke(product: Just<A>): R =
//    this(product.component1())
// operator fun <A, B, R> ((A, B) -> R).invoke(product: And<A, B>): R =
//    this.invoke(product.component1(), product.component2())
// operator fun <A, B, C, R> ((A, B, C) -> R).invoke(product: And<And<A, B>, C>): R =
//    this(product.component1(), product.component2(), product.component3())
