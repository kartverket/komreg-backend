package no.kartverket.komreg.transformation.rule3.range

import no.kartverket.komreg.core.util.kotlin.TypeTag
import no.kartverket.komreg.core.util.kotlin.typeClosure
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.primaryConstructor

abstract class DiscreteDomain<A> {

    abstract val typeTag: TypeTag<A>

    abstract fun touches(a: A, b: A?): Boolean

    @Suppress("NOTHING_TO_INLINE")
    @JvmName("infixTouches")
    inline infix fun A.touches(other: A): Boolean {
        return touches(this, other)
    }

    override fun toString(): String {
        return "${this::class.simpleName ?: "DiscreteDomain"}<${typeTag}>"
    }

    final override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    final override fun hashCode(): Int {
        return super.hashCode()
    }

    object Registry {
        private val map = ConcurrentHashMap<TypeTag<*>, DiscreteDomain<*>>()

        init {
            register(Float.MIN_VALUE, Float.MAX_VALUE, Float::nextUp, Float::nextDown)
            register(Double.MIN_VALUE, Double.MAX_VALUE, Double::nextUp, Double::nextDown)

            register(Long.MIN_VALUE, Long.MAX_VALUE, Long::inc, Long::dec)
            register(Int.MIN_VALUE, Int.MAX_VALUE, Int::inc, Int::dec)
            register(Short.MIN_VALUE, Short.MAX_VALUE, Short::inc, Short::dec)
            register(Char.MIN_VALUE, Char.MAX_VALUE, Char::inc, Char::dec)
            register(Byte.MIN_VALUE, Byte.MAX_VALUE, Byte::inc, Byte::dec)
        }

        private inline fun <reified A : Comparable<A>> register(minValue: A, maxValue: A, crossinline inc: A.() -> A, crossinline dec: A.() -> A) {
            require(minValue < maxValue)
            val typeTag = TypeTag<A>()
            map[typeTag] = object : DiscreteDomain<A>() {
                override val typeTag = typeTag
                override fun touches(a: A, b: A?): Boolean {
                    return if (b == null) {
                        a == minValue || a == maxValue
                    } else {
                        a == b || (a != maxValue && a.inc() == b) || (a != minValue && a.dec() == b)
                    }
                }
            }
        }

        operator fun <A> get(typeTag: TypeTag<A>): DiscreteDomain<A> {
            return getOrNull(typeTag) ?: object : DiscreteDomain<A>() {
                override val typeTag = typeTag

                override fun touches(a: A, b: A?): Boolean {
                    return false
                }

                override fun toString(): String {
                    return "ContinuousDomain<$typeTag>"
                }
            }
        }

        private fun <A> getOrNull(typeTag: TypeTag<A>): DiscreteDomain<A>? {
            @Suppress("UNCHECKED_CAST")
            val discreteDomain = map[typeTag] as? DiscreteDomain<A>
            val result = (discreteDomain
                ?: typeClosure(typeTag.type)
                    .mapNotNullTo(ArrayList()) { type ->
                        map[TypeTag(type)]
                    }
                    .reduceOrNull { a, b ->
                        if (a.typeTag.type == b.typeTag.type || a.typeTag.type.isSubtypeOf(b.typeTag.type)) {
                            a
                        } else if (b.typeTag.type.isSubtypeOf(a.typeTag.type)) {
                            b
                        } else {
                            error("No discrete domain for $typeTag")
                        }
                    }
                    .let {
                        @Suppress("UNCHECKED_CAST")
                        it as? DiscreteDomain<A>?
                    })
            if (result == null) {
                val classifier = typeTag.type.classifier
                if (classifier is KClass<*> && classifier.isData) {
                    val singleDataParam = classifier.primaryConstructor?.parameters?.singleOrNull()?.let { ctorParam ->
                        classifier.declaredMemberProperties.singleOrNull { it.name == ctorParam.name && it.isFinal && !it.returnType.isMarkedNullable } as KProperty1<A, Any>?
                    }

                    if (singleDataParam != null) {
                        val innerTypeTag = TypeTag(singleDataParam.returnType) as TypeTag<Any>
                        val innerDomain = getOrNull(innerTypeTag) as? DiscreteDomain<Any>
                        if (innerDomain != null) {
                            return map.computeIfAbsent(typeTag) { _ ->
                                object : DiscreteDomain<A>() {
                                    override val typeTag = typeTag

                                    override fun touches(a: A, b: A?): Boolean {
                                        val aInner = singleDataParam.get(a)
                                        val bInner = b?.let(singleDataParam::get)
                                        return innerDomain.touches(aInner, bInner)
                                    }

                                    override fun toString(): String {
                                        return "DerivedDiscreteDomain<${typeTag.type}>(val ${singleDataParam.name} : ${innerTypeTag.type})"
                                    }
                                }
                            } as DiscreteDomain<A>
                        }
                    }
                }
            }
            return result
        }

    }
}

infix fun <A : Comparable<A>> A.touches(other: A?): Boolean {
    // Dette burde være greit pga A : Comparable<A>?
    @Suppress("UNCHECKED_CAST")
    val classifier = this::class as KClass<in A>
    return DiscreteDomain.Registry[TypeTag(classifier)].touches(this, other)
}