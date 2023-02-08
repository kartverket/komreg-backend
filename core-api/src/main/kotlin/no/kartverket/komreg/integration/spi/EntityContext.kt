package no.kartverket.komreg.integration.spi

import arrow.core.*
import no.kartverket.komreg.core.KrData
import no.kartverket.komreg.core.Product
import no.kartverket.komreg.core.spi.Normalizer
import java.util.ServiceLoader
import kotlin.reflect.KClass

class EntityContext<out A> private constructor(
    private val sourceContext: SourceEntityContext<Product<*>>,
    private val data: KrData<A>,
) : SourceEntityContext<Product<*>> by sourceContext {

    fun <B> map(f: (A) -> B) = EntityContext(sourceContext, data.map(f))

    override fun toString(): String {
        return "EntityContext(id=$entityId, data=$data)"
    }

    companion object {
        private val normalizers: Map<KClass<out Any>, Normalizer<*>> =
            ServiceLoader.load(Normalizer::class.java).associateBy { it.type }

        fun SourceEntityContext<*>.toEntityContext(): EntityContext<Product<*>> {
            val data = this.sourceEntity.map { product ->
                val allNormalizedElems = product.elems
                    .flatMap { (elemType, elem) ->
                        @Suppress("UNCHECKED_CAST")
                        val normalizedElems = (normalizers[elemType.classifier] as Normalizer<Any>?)
                            ?.normalize(elem)?.elems
                            ?: (elemType to elem).nel()

                        normalizedElems.also {
                            if (it.any { (normalizedType, _) -> normalizers.containsKey(normalizedType.classifier) }) {
                                throw IllegalStateException()
                            }
                        }
                    }
                    .toNonEmptyListOrNull()!!
                when (allNormalizedElems.size) {
                    1 -> Product.Just<Any>(allNormalizedElems)
                    else -> Product.And<Any, Any>(allNormalizedElems)
                }
            }
            return EntityContext(this, data)
        }
    }
}

interface SourceEntityContext<out A : Product<*>> {
    val entityId: Any
    val sourceEntity: KrData<A>

    data class Simple<out A : Product<*>>(override val entityId: Any, override val sourceEntity: KrData<A>) :
        SourceEntityContext<A>

    companion object {
        operator fun <A : Product<*>> invoke(entityId: Any, sourceEntity: KrData<A>) = Simple(entityId, sourceEntity)
    }
}
