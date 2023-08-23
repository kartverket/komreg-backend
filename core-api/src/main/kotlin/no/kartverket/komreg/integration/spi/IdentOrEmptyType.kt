package no.kartverket.komreg.integration.spi

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptyListOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import no.kartverket.komreg.core.util.kotlin.typeClosure
import no.kartverket.komreg.integration.spi.Ident.Empty.appendWith
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.TreeMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

/**
 * En beskrivelse av en ident, som består av ett sett av kotlin typer, og en
 * rekkefølge identen har verdier av disse typene i.
 */
class IdentType<T : Ident, A : Comparable<A>> private constructor(
    val prefix: IdentOrEmptyType<T>,
    last: KType
) : IdentOrEmptyType<Ident.And<T, A>>() {
    companion object {
        private class CacheEntry<T : IdentType<*, *>>(type: T) : WeakReference<T>(type, refQueue) {
            val kotlinTypes = type.types
        }

        private val cache: MutableMap<NonEmptyList<KType>, CacheEntry<*>> = mutableMapOf()
        private val refQueue: ReferenceQueue<IdentOrEmptyType<*>> = ReferenceQueue()
        private val cacheLock = Mutex()

        internal suspend fun makeOrGetFromCache(kotlinTypes: NonEmptyList<KType>): IdentType<*, *> =
            makeOrGetFromCache(kotlinTypes, true)

        private suspend fun makeOrGetFromCache(
            kotlinTypes: NonEmptyList<KType>,
            removeUnused: Boolean
        ): IdentType<*, *> {
            val firstTryCachedType: IdentType<*, *>?
            val queued: CacheEntry<*>?

            cacheLock.withLock {
                firstTryCachedType = cache[kotlinTypes]?.get()
                queued =
                    if (removeUnused)
                        refQueue.poll() as CacheEntry<*>?
                    else
                        null
            }

            if (firstTryCachedType != null && queued == null) {
                return firstTryCachedType
            }

            val createdType =
                if (firstTryCachedType == null) {
                    invoke(
                        kotlinTypes.dropLast(1).toNonEmptyListOrNull()?.let { makeOrGetFromCache(it) } ?: EmptyIdentType,
                        kotlinTypes.last()
                    )
                } else null

            return cacheLock.withLock {
                var nextQueued = queued
                while (nextQueued != null) {
                    cache.remove(nextQueued.kotlinTypes)
                    nextQueued = refQueue.poll() as CacheEntry<*>?
                }
                if (createdType != null) {
                    val cachedType = cache[kotlinTypes]?.get()
                    if (cachedType == null) {
                        cache[kotlinTypes] = CacheEntry(createdType)
                        createdType
                    } else {
                        cachedType
                    }
                } else {
                    firstTryCachedType!!
                }
            }
        }

        operator fun <T : Ident> invoke(prefix: IdentOrEmptyType<T>, last: KType): IdentType<T, *> =
            IdentType<T, Nothing>(prefix, last)

    }

    init {
        val classifier = last.classifier
        require(classifier is KClass<*>) { "Unrepresentable type: $last" }
    }

    override val types: List<KType> = prefix.types + last

    override val typeIndices: Map<KType, Set<Int>> = types
        .flatMapIndexed { n, kType -> typeClosure(kType).map { kType to n } }
        .groupingBy { it.first }
        .aggregate<Pair<KType, Int>, KType, MutableSet<Int>> { _, accumulator, element, _ ->
            (accumulator ?: mutableSetOf()).apply {
                this.add(element.second)
            }
        }


    override val bottomTypes: Map<KType, Int> = typeIndices
        .mapNotNull { (kType, indices) ->
            indices.singleOrNull()?.let { kType to it }
        }
        .toMap()
        .also { map ->
            if (map.size != types.size) {
                throw IllegalArgumentException("Some types does not have a distinct index: ${
                    types
                        .map { it to typeIndices[it].orEmpty().sorted() }
                        .filter { it.second.size != 1 }
                        .toMap(TreeMap { a, b -> a.toString().compareTo(b.toString()) })   
                }")
            }
        }

    override val rawTypes: List<KClass<out Comparable<Comparable<*>>>> = types
        .map {
            @Suppress("UNCHECKED_CAST")
            it.classifier as KClass<out Comparable<Comparable<*>>>
        }

    @Suppress("UNCHECKED_CAST")
    suspend inline fun <reified  V : Comparable<V>> append(): IdentType<Ident.And<T, A>, V> =
        identTypeFromKotlinTypes(types.first(), *(types.drop(1) + typeOf<V>()).toTypedArray()) as IdentType<Ident.And<T, A>, V>

    @Suppress("UNCHECKED_CAST")
    fun T.append(value: A) : Ident.And<T, A> {
        val identAppended = when(this) {
            is Ident.Empty -> {
                appendWith(this@IdentType as IdentType<Ident.Empty, A>, value)
            }
            is Ident.And<*, *> -> {
                unsafeAppendAndWith(value)
            }
            else -> throw IllegalStateException("Unknown ident type")
        }
        return identAppended as Ident.And<T, A>
    }

    @Suppress("UNCHECKED_CAST")
    private fun <U : Ident, B : Comparable<B>> Ident.And<U, B>.unsafeAppendAndWith(value: A) : Ident.And<Ident.And<U, B>, A> =
        this.appendWith(this@IdentType as IdentType<Ident.And<U, B>, A>, value)

    override fun toString(): String {
        return this::class.simpleName + ": " + types.toString()
    }
}

/** Sum type for IdentType og EmptyIdentType */
@Serializable(IdentTypeSerializer::class)
sealed class IdentOrEmptyType<T : Ident> {


    /**
     * Kotlin typer som denne IdentType er satt sammen av i en spesifikk
     * rekkefølge
     */
    abstract val types: List<KType>

    /**
     * Alle kotlintyper denne IdentTypen er satt samman av, inkludert alle
     * supertyper som ikke har en unik indeks
     */
    abstract val typeIndices: Map<KType, Set<Int>>

    /**
     * Kotlin typer som denne IdentType er satt sammen av, som har en unik
     * indeks
     */
    abstract val bottomTypes: Map<KType, Int>

    /**
     * Rå typer som denne IdentType er satt sammen av, i en spesifikk rekkefølge
     */
    abstract val rawTypes: List<KClass<out Comparable<Comparable<*>>>>

    /** Lengden på identer av denne IdentTypen */
    val size: Int get() = types.size

    /**
     * Hent ut en identmapper for denne IdentTypen, hvis det er mulig å lage.
     * Den er mulig å lage hvis alle typer i måltypen er en subtype av en av
     * en type i kildetypen med unik indeks.
     */
    fun <A : Comparable<A>, B : Ident> createMapper(targetType: IdentType<B, A>): IdentMapper<T, Ident.And<B, A>>? {
        val indexMap = createIndexMapping(targetType)
        val allSourceElems = (0 until size).all {
            indexMap.containsValue(it)
        }
        val allTargetElems = (0 until targetType.size).all {
            indexMap.containsValue(it)
        }

        return if (allTargetElems) {
            IdentMapper(allSourceElems) { source ->
                val sourceArray = source.toArray()
                val targetArray = Array<Comparable<*>>(targetType.size) { n ->
                    sourceArray[indexMap[n]!!]
                }
                @Suppress("UNCHECKED_CAST")
                identWithTypeOrThrow(targetType, *targetArray) as Ident.And<B, A>
            }
        } else {
            null
        }
    }
}

/** Mapper fra en ident av en type [T] til en annen ident av typen[U] */
class IdentMapper<T : Ident, U : Ident>(
    /** Om denne mapperen mapper alle elementer fra kildetypen */
    val complete: Boolean,
    mapFun: (T) -> U
) : (T) -> U by mapFun

/** IdentType for den tomme identen */
object EmptyIdentType : IdentOrEmptyType<Ident.Empty>() {
    override val types: List<KType> = emptyList()
    override val typeIndices: Map<KType, Set<Int>> = emptyMap()
    override val bottomTypes: Map<KType, Int> = emptyMap()
    override val rawTypes: List<KClass<out Comparable<Comparable<*>>>> = emptyList()

    @Suppress("UNCHECKED_CAST")
    suspend inline fun <reified  V : Comparable<V>> append(): IdentType<Ident.Empty, V> =
        identTypeFromKotlinTypes(typeOf<V>()) as IdentType<Ident.Empty, V>

    override fun toString(): String {
        return this::class.simpleName.orEmpty()
    }
}



typealias IdentType1<A> = IdentType<Ident.Empty, A>
typealias IdentType2<A, B> = IdentType<Ident1<A>, B>
typealias IdentType3<A, B, C> = IdentType<Ident2<A, B>, C>
typealias IdentType4<A, B, C, D> = IdentType<Ident3<A, B, C>, D>
typealias IdentType5<A, B, C, D, E> = IdentType<Ident4<A, B, C, D>, E>
typealias IdentType6<A, B, C, D, E, F> = IdentType<Ident5<A, B, C, D, E>, F>
typealias IdentType7<A, B, C, D, E, F, G> = IdentType<Ident6<A, B, C, D, E, F>, G>
typealias IdentType8<A, B, C, D, E, F, G, H> = IdentType<Ident7<A, B, C, D, E, F, G>, H>

fun <A : Comparable<A>, T : Ident> IdentType<Ident.And<T, A>, *>.dropLast(): IdentType<T, A> =
    this.prefix as IdentType<T, A>

suspend inline fun <reified A>
        identTypeOf1(): IdentType<Ident.Empty, A>
        where A : Comparable<A> {
    @Suppress("UNCHECKED_CAST")
    return identTypeFromKotlinTypes(typeOf<A>()) as IdentType<Ident.Empty, A>
}

suspend inline fun <reified A, reified B>
        identTypeOf2(): IdentType<Ident.And<Ident.Empty, A>, B>
        where A : Comparable<A>, B : Comparable<B> {
    @Suppress("UNCHECKED_CAST")
    return identTypeFromKotlinTypes(
            typeOf<A>(),
            typeOf<B>()

    ) as IdentType<Ident.And<Ident.Empty, A>, B>
}

suspend inline fun <reified A, reified B, reified C>
        identTypeOf3(): IdentType<Ident2<A, B>, C>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C> {
    @Suppress("UNCHECKED_CAST")
    return identTypeFromKotlinTypes(
            typeOf<A>(),
            typeOf<B>(),
            typeOf<C>()

    ) as IdentType<Ident2<A, B>, C>
}

suspend inline fun <reified A, reified B, reified C, reified D>
        identTypeOf4(): IdentType<Ident3<A, B, C>, D>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>, D : Comparable<D> {
    @Suppress("UNCHECKED_CAST")
    return identTypeFromKotlinTypes(
            typeOf<A>(),
            typeOf<B>(),
            typeOf<C>(),
            typeOf<D>()

    ) as IdentType<Ident3<A, B, C>, D>
}

suspend inline fun <reified A, reified B, reified C, reified D, reified E>
        identTypeOf5(): IdentType<Ident4<A, B, C, D>, E>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E> {
    @Suppress("UNCHECKED_CAST")
    return identTypeFromKotlinTypes(
            typeOf<A>(),
            typeOf<B>(),
            typeOf<C>(),
            typeOf<D>(),
            typeOf<E>()

    ) as IdentType<Ident4<A, B, C, D>, E>
}

suspend inline fun
        <reified A, reified B, reified C, reified D, reified E, reified F>
        identTypeOf6(): IdentType<Ident5<A, B, C, D, E>, F>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E>, F : Comparable<F> {
    @Suppress("UNCHECKED_CAST")
    return identTypeFromKotlinTypes(
            typeOf<A>(),
            typeOf<B>(),
            typeOf<C>(),
            typeOf<D>(),
            typeOf<E>(),
            typeOf<F>()
    ) as IdentType<Ident5<A, B, C, D, E>, F>
}

suspend inline fun <reified A, reified B, reified C, reified D, reified E, reified F, reified G>
        identTypeOf7(): IdentType<Ident6<A, B, C, D, E, F>, G>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E>, F : Comparable<F>,
              G : Comparable<G> {
    @Suppress("UNCHECKED_CAST")
    return identTypeFromKotlinTypes(
            typeOf<A>(),
            typeOf<B>(),
            typeOf<C>(),
            typeOf<D>(),
            typeOf<E>(),
            typeOf<F>(),
            typeOf<G>()
    ) as IdentType<Ident6<A, B, C, D, E, F>, G>
}

suspend inline fun <reified A, reified B, reified C, reified D, reified E, reified F, reified G, reified H>
        identTypeOf8(): IdentType<Ident7<A, B, C, D, E, F, G>, H>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E>, F : Comparable<F>,
              G : Comparable<G>, H : Comparable<H> {
    @Suppress("UNCHECKED_CAST")
    return identTypeFromKotlinTypes(
            typeOf<A>(),
            typeOf<B>(),
            typeOf<C>(),
            typeOf<D>(),
            typeOf<E>(),
            typeOf<F>(),
            typeOf<G>(),
            typeOf<H>()
    ) as IdentType<Ident7<A, B, C, D, E, F, G>, H>
}

/**
 * Hent fra cache, eller lag og cache en IdentType for en liste av kotlin typer.
 *
 * Cachen er weak, så den vil bli fjernet når det ikke er noen referanser til den igjen.
 *
 * Dette er en suspend funksjon, fordi vi må låse på cachen - og en tradisjonell
 * lås vil blokkere tråden, og dermed kunne føre til at man sulter threadpoolen med påfølgende
 * deadlock når denne blir kalt fra en coroutine (og by extension; også flows).
 */
suspend fun identTypeFromKotlinTypes(first: KType, vararg more: KType): IdentType<*,*> {
    return IdentType.makeOrGetFromCache(nonEmptyListOf(first, *more))
}

private fun IdentOrEmptyType<*>.createIndexMapping(
    targetType: IdentType<*, *>
) : Map<Int, Int> = targetType
    .types
    .mapIndexedNotNull { targetElemIndex, targetElemType ->
        this.bottomTypes[targetElemType]?.let { it to targetElemIndex }
    }
    .toMap()

@OptIn(ExperimentalSerializationApi::class, ExperimentalStdlibApi::class)
class IdentTypeSerializer : KSerializer<IdentOrEmptyType<*>> {
    private val elementSerializer = String.serializer()

    override val descriptor: SerialDescriptor = listSerialDescriptor(elementSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: IdentOrEmptyType<*>) {
        encoder.beginCollection(descriptor, value.size).apply {
            value.types.forEachIndexed { index, element ->
                encodeSerializableElement(descriptor, index, elementSerializer, element.javaType.typeName)
            }
        }.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): IdentOrEmptyType<*> {
        val types = decoder.decodeStructure(descriptor) {
            val types = mutableListOf<KType>()
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                val typeString = decodeSerializableElement(descriptor, index, elementSerializer)
                val c = Class.forName(typeString) // TODO: Dette virker ikke for alle typer
                val type = c.kotlin.starProjectedType // TODO: Blant annet så blir ikke eventuelle typeparametre med
                types.add(type)
            }
            types
        }
        return runBlocking { identTypeFromKotlinTypes(types.first(), *types.drop(1).toTypedArray()) }
    }
}
