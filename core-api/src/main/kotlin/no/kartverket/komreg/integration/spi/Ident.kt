@file:Suppress("NOTHING_TO_INLINE")

package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.integration.spi.Ident.And
import no.kartverket.komreg.integration.spi.Ident.Empty
import kotlin.reflect.cast
import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf

/**
 * Ident er en forretningsidentifikator for entiteter.
 *
 * Ident er en immutabel wrapper rundt én eller flere verdier, og kan sees på
 * som ett by-type-map som gjør statisk(compiletime) oppslag for verdiene for en
 * gitt type. Disse oppslagene støtter også subtyping, så man kan i prinsippet
 * slå opp supertyper - så lenge supertypen har en unik indeks i identen.
 *
 * En annen forskjell fra et tradisjonelt map er at rekkefølgen på verdiene
 * utgjør en del av identtypen. For å komme rundt dette, så er det laget en
 * metode som lager en mappingfunksjon mellom identer som inneholder de samme
 * verditypene i en annen rekkefølge.
 *
 * For identifikatorer med 1-8 verdier er det spesialiserte typer som gjør at
 * man bruker minst mulig minne for disse. Fra 9 verdier og oppover er det ett
 * array som brukes i bakkant.
 *
 * Det er imidlertid ikke laget accessor-metoder for identer med flere enn 8
 * verdier.
 *
 * For å kunne "gjenskape" compiletime typeinformasjonen til identen, så er
 * det laget en egen type [IdentType] som tar vare på denne informasjonen. Disse
 * blir cachet, så alle identer med samme typeparametere vil i utgangspunktet
 * ha samme [IdentType] instans (minnebrukoptimalisering).
 *
 *
 */
sealed class Ident private constructor() {

    @Suppress("DeprecatedCallableAddReplaceWith")
    companion object {
        @Deprecated("Create an identTypeOf1(), and use invoke on that instead")
        suspend inline operator fun <reified A> invoke(
            a: A
        ): Ident1<A> where A : Comparable<A> =
            identTypeOf1<A>()(a)

        @Deprecated("Create an identTypeOf2(), and use invoke on that instead")
        suspend inline operator fun <reified A, reified B> invoke(
            a: A, b: B
        ): Ident2<A, B> where A : Comparable<A>, B : Comparable<B> =
            identTypeOf2<A, B>()(a, b)

        @Deprecated("Create an identTypeOf3(), and use invoke on that instead")
        suspend inline operator fun <reified A, reified B, reified C> invoke(
            a: A, b: B, c: C
        ): Ident3<A, B, C> where A : Comparable<A>, B : Comparable<B>,
                                 C : Comparable<C> =
            identTypeOf3<A, B, C>()(a, b, c)

        @Deprecated("Create an identTypeOf4(), and use invoke on that instead")
        suspend inline operator
        fun <reified A, reified B, reified C, reified D> invoke(
            a: A, b: B, c: C, d: D
        ): Ident4<A, B, C, D> where A : Comparable<A>, B : Comparable<B>,
                                    C : Comparable<C>, D : Comparable<D> =
            identTypeOf4<A, B, C, D>()(a, b, c, d)

        @Deprecated("Create an identTypeOf5(), and use invoke on that instead")
        suspend inline operator
        fun <reified A, reified B, reified C, reified D, reified E> invoke(
            a: A, b: B, c: C, d: D, e: E
        ): Ident5<A, B, C, D, E>
                where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
                      D : Comparable<D>, E : Comparable<E> =
            identTypeOf5<A, B, C, D, E>()(a, b, c, d, e)

        @Deprecated("Create an identTypeOf6(), and use invoke on that instead")
        suspend inline operator
        fun <reified A, reified B, reified C, reified D, reified E, reified F> invoke(
            a: A, b: B, c: C, d: D, e: E, f: F
        ): Ident6<A, B, C, D, E, F>
                where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
                      D : Comparable<D>, E : Comparable<E>, F : Comparable<F> =
            identTypeOf6<A, B, C, D, E, F>()(a, b, c, d, e, f)

        @Deprecated("Create an identTypeOf7(), and use invoke on that instead")
        suspend inline operator
        fun <reified A, reified B, reified C, reified D, reified E, reified F, reified G> invoke(
            a: A, b: B, c: C, d: D, e: E, f: F, g: G
        ): Ident7<A, B, C, D, E, F, G>
                where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
                      D : Comparable<D>, E : Comparable<E>, F : Comparable<F>,
                      G : Comparable<G> =
            identTypeOf7<A, B, C, D, E, F, G>()(a, b, c, d, e, f, g)

        @Deprecated("Create an identTypeOf8(), and use invoke on that instead")
        suspend inline operator
        fun <reified A, reified B, reified C, reified D, reified E, reified F, reified G, reified H> invoke(
            a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H
        ): Ident8<A, B, C, D, E, F, G, H>
                where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
                      D : Comparable<D>, E : Comparable<E>, F : Comparable<F>,
                      G : Comparable<G>, H : Comparable<H> =
            identTypeOf8<A, B, C, D, E, F, G, H>()(a, b, c, d, e, f, g, h)
    }

    /**
     * Typeinformasjonen til identen.
     */
    abstract val type: IdentOrEmptyType<*>

    /**
     * Lengden på identen.
     */
    val size: Int get() = type.size

    /**
     * Lag en ny ident med en ny verdi for verdien på indeks [n].
     *
     * @param n indeks til verdien som skal oppdateres
     * @param f en funksjon som tar inn en hvilken som helst [Comparable] og
     *          returnerer en ny verdi av samme type som allerde er i identen
     *          på indeks [n]
     * @return en ny ident med ny verdi for indeks [n]
     * @throws ClassCastException hvis [f] returnerer en verdi som ikke er av
     *                            samme type som verdien på indeks [n]
     */
    abstract fun updateOrThrow(
        n: Int,
        f: (Comparable<*>) -> Comparable<*>
    ): Ident

    /**
     * Verdiene i identen som en array.
     */
    abstract fun toArray(): Array<Comparable<*>>

    /**
     * Den tomme identen.
     */
    object Empty : Ident() {
        override val type: EmptyIdentType get() = EmptyIdentType

        override fun updateOrThrow(
            n: Int,
            f: (Comparable<*>) -> Comparable<*>
        ): Empty = this

        override fun toArray(): Array<Comparable<*>> = emptyArray()

        fun <V : Comparable<V>> appendWith(
            type: IdentType<Empty, V>,
            value: V
        ): Ident1<V> = OfExactly1(type, value)
    }

    /**
     * En ident med én eller flere verdi(er), men compiletime ukjent type.
     */
    sealed class NonEmpty : Ident() {
        /**
         * Den siste verdien i identen.
         */
        abstract val last: Comparable<*>

        /**
         * Lag en ny ident med siste verdi fjernet.
         */
        abstract fun dropLast(): Ident
    }

    /**
     * En ident med én eller flere verdi(er) og compiletime kjent type.
     */
    sealed class And<T : Ident, A : Comparable<A>>
        : NonEmpty(), Comparable<And<T, A>> {

        abstract override val last: A
        abstract override val type: IdentType<T, A>
        abstract override fun dropLast(): T
        abstract fun <V : Comparable<V>> appendWith(
            type: IdentType<And<T, A>, V>,
            value: V
        ): And<And<T, A>, V>

        /**
         * Lag en ny ident med en ny(e) verdi(er) for en gitt type [V].
         *
         * @param f en funksjon som tar inn en verdi av type [V] og returnerer
         *        en ny verdi av type [V]
         * @throws NoSuchElementException hvis [V] ikke finnes i identen
         * @return en ny ident med ny verdi for [V]
         */
        inline fun <reified V : Comparable<V>> updateOrThrow(
            noinline f: (V) -> V
        ): And<T, A> {
            val kotlinType = typeOf<V>()
            val indices = this.type.typeIndices[kotlinType].orEmpty()
            if (indices.isEmpty()) {
                throw NoSuchElementException(this.type.toString())
            }

            @Suppress("UNCHECKED_CAST")
            val ff = f as (Comparable<*>) -> Comparable<*>
            return indices.fold(this) { acc, i ->
                acc.updateOrThrow(i, ff)
            }
        }

        override fun updateOrThrow(
            n: Int,
            f: (Comparable<*>) -> Comparable<*>
        ): And<T, A> {
            val ff = { value: Comparable<*> ->
                f(value).also {
                    if (!type.types[n].isSupertypeOf(it::class.starProjectedType)) {
                        throw ClassCastException("Cannot cast ${it::class} to ${type.types[n]}")
                    }
                }
            }
            return updateOrThrowImpl(n, ff)
        }

        protected abstract fun updateOrThrowImpl(
            n: Int,
            f: (Comparable<*>) -> Comparable<*>
        ): And<T, A>

    }
}

// Typealiaser for identer med kjent type og lengde compiletime
typealias Ident1<A> = And<Empty, A>
typealias Ident2<A, B> = And<And<Empty, A>, B>
typealias Ident3<A, B, C> = And<And<And<Empty, A>, B>, C>
typealias Ident4<A, B, C, D> = And<And<And<And<Empty, A>, B>, C>, D>
typealias Ident5<A, B, C, D, E> = And<And<And<And<And<Empty, A>, B>, C>, D>, E>
typealias Ident6<A, B, C, D, E, F> = And<And<And<And<And<And<Empty, A>, B>, C>, D>, E>, F>
typealias Ident7<A, B, C, D, E, F, G> = And<And<And<And<And<And<And<Empty, A>, B>, C>, D>, E>, F>, G>
typealias Ident8<A, B, C, D, E, F, G, H> = And<And<And<And<And<And<And<And<Empty, A>, B>, C>, D>, E>, F>, G>, H>

// Typealiaser for konsumenter av identer med delvis kjent type compiletime ("slutter på")
typealias IdentTail1<A> = And<out Ident, A>
typealias IdentTail2<A, B> = And<out And<out Ident, A>, B>
typealias IdentTail3<A, B, C> = And<out And<out And<out Ident, A>, B>, C>
typealias IdentTail4<A, B, C, D> = And<out And<out And<out And<out Ident, A>, B>, C>, D>
typealias IdentTail5<A, B, C, D, E> = And<out And<out And<out And<out And<out Ident, A>, B>, C>, D>, E>
typealias IdentTail6<A, B, C, D, E, F> = And<out And<out And<out And<out And<out And<out Ident, A>, B>, C>, D>, E>, F>
typealias IdentTail7<A, B, C, D, E, F, G> = And<out And<out And<out And<out And<out And<out And<out Ident, A>, B>, C>, D>, E>, F>, G>
typealias IdentTail8<A, B, C, D, E, F, G, H> = And<out And<out And<out And<out And<out And<out And<out And<out Ident, A>, B>, C>, D>, E>, F>, G>, H>

// Typealiaser for konsumenter av identer med kjent type og lengde compiletime)
typealias IdentIn1<A> = And<Empty, A>
typealias IdentIn2<A, B> = And<out And<Empty, A>, B>
typealias IdentIn3<A, B, C> = And<out And<out And<Empty, A>, B>, C>
typealias IdentIn4<A, B, C, D> = And<out And<out And<out And<Empty, A>, B>, C>, D>
typealias IdentIn5<A, B, C, D, E> = And<out And<out And<out And<out And<Empty, A>, B>, C>, D>, E>
typealias IdentIn6<A, B, C, D, E, F> = And<out And<out And<out And<out And<out And<Empty, A>, B>, C>, D>, E>, F>
typealias IdentIn7<A, B, C, D, E, F, G> = And<out And<out And<out And<out And<out And<out And<Empty, A>, B>, C>, D>, E>, F>, G>
typealias IdentIn8<A, B, C, D, E, F, G, H> = And<out And<out And<out And<out And<out And<out And<out And<Empty, A>, B>, C>, D>, E>, F>, G>, H>

@JvmName("getLast1")
operator fun <A : Comparable<A>> IdentTail1<A>.invoke(): A = last

@Suppress("UNCHECKED_CAST")
@JvmName("getLast2")
operator fun <A : Comparable<A>> IdentTail2<A, *>.invoke(): A =
    (this as OfAtLeast2<*, A, *>).n2

@Suppress("UNCHECKED_CAST")
@JvmName("getLast3")
operator fun <A : Comparable<A>> IdentTail3<A, *, *>.invoke(): A =
    (this as OfAtLeast3<*, A, *, *>).n3

@Suppress("UNCHECKED_CAST")
@JvmName("getLast4")
operator fun <A : Comparable<A>> IdentTail4<A, *, *, *>.invoke(): A =
    (this as OfAtLeast4<*, A, *, *, *>).n4

@Suppress("UNCHECKED_CAST")
@JvmName("getLast5")
operator fun <A : Comparable<A>> IdentTail5<A, *, *, *, *>.invoke(): A =
    (this as OfAtLeast5<*, A, *, *, *, *>).n5

@Suppress("UNCHECKED_CAST")
@JvmName("getLast6")
operator fun <A : Comparable<A>> IdentTail6<A, *, *, *, *, *>.invoke(): A =
    (this as OfAtLeast6<*, A, *, *, *, *, *>).n6

@Suppress("UNCHECKED_CAST")
@JvmName("getLast7")
operator fun <A : Comparable<A>> IdentTail7<A, *, *, *, *, *, *>.invoke(): A =
    (this as OfAtLeast7<*, A, *, *, *, *, *, *>).n7

@Suppress("UNCHECKED_CAST")
@JvmName("getLast8")
operator fun <A : Comparable<A>> IdentTail8<A, *, *, *, *, *, *, *>.invoke(): A =
    (this as OfAtLeast8<*, A, *, *, *, *, *, *, *>).n8

@Suppress("UNCHECKED_CAST")
@JvmName("updateLast1")
inline fun <T : IdentTail1<A>, A : Comparable<A>> T.update(noinline f: (A) -> A): T =
    updateOrThrow(this.size - 1, f as (Comparable<*>) -> Comparable<*>) as T

@Suppress("UNCHECKED_CAST")
@JvmName("updateLast2")
inline fun <T : IdentTail2<A, *>, A : Comparable<A>> T.update(noinline f: (A) -> A): T =
    updateOrThrow(this.size - 2, f as (Comparable<*>) -> Comparable<*>) as T

@Suppress("UNCHECKED_CAST")
@JvmName("updateLast3")
inline fun <T : IdentTail3<A, *, *>, A : Comparable<A>> T.update(noinline f: (A) -> A): T =
    updateOrThrow(this.size - 3, f as (Comparable<*>) -> Comparable<*>) as T

@Suppress("UNCHECKED_CAST")
@JvmName("updateLast4")
inline fun <T : IdentTail4<A, *, *, *>, A : Comparable<A>> T.update(noinline f: (A) -> A): T =
    updateOrThrow(this.size - 4, f as (Comparable<*>) -> Comparable<*>) as T

@Suppress("UNCHECKED_CAST")
@JvmName("updateLast5")
inline fun <T : IdentTail5<A, *, *, *, *>, A : Comparable<A>> T.update(noinline f: (A) -> A): T =
    updateOrThrow(this.size - 5, f as (Comparable<*>) -> Comparable<*>) as T

@Suppress("UNCHECKED_CAST")
@JvmName("updateLast6")
inline fun <T : IdentTail6<A, *, *, *, *, *>, A : Comparable<A>> T.update(noinline f: (A) -> A): T =
    updateOrThrow(this.size - 6, f as (Comparable<*>) -> Comparable<*>) as T

@Suppress("UNCHECKED_CAST")
@JvmName("updateLast7")
inline fun <T : IdentTail7<A, *, *, *, *, *, *>, A : Comparable<A>> T.update(noinline f: (A) -> A): T =
    updateOrThrow(this.size - 7, f as (Comparable<*>) -> Comparable<*>) as T

@Suppress("UNCHECKED_CAST")
@JvmName("updateLast8")
inline fun <T : IdentTail8<A, *, *, *, *, *, *, *>, A : Comparable<A>> T.update(noinline f: (A) -> A): T =
    updateOrThrow(this.size - 8, f as (Comparable<*>) -> Comparable<*>) as T

/**
 * Hent verdien av typen [A] fra identen.
 *
 * @return verdien av typen [A] fra identen.
 * @throws NoSuchElementException hvis identen ikke inneholder en verdi av typen
 *         [A], eller flere enn én verdi av typen [A] finnes i identen.
 */
inline fun <reified A : Comparable<A>> Ident.getOrThrow(): A {
    val aType = A::class.starProjectedType
    val index = type.bottomTypes[aType]
        ?: throw NoSuchElementException(aType.toString())
    return getOrThrow(index) as A
}

/**
 * Hent verdien på indeks [n] fra identen.
 *
 * @param n indeks til verdien som skal hentes.
 * @return verdien på indeks [n] fra identen.
 * @throws IndexOutOfBoundsException hvis [n] er større enn antall verdier i
 *         identen.
 */
fun Ident.getOrThrow(n: Int): Comparable<*> {
    val offsetFromLast = (size - 1) - n
    if (offsetFromLast < 0) {
        throw IndexOutOfBoundsException(n.toString())
    }
    try {
        return when (offsetFromLast) {
            0 -> (this as And<*, *>).last
            1 -> (this as (OfAtLeast2<*, *, *>)).n2
            2 -> (this as (OfAtLeast3<*, *, *, *>)).n3
            3 -> (this as (OfAtLeast4<*, *, *, *, *>)).n4
            4 -> (this as (OfAtLeast5<*, *, *, *, *, *>)).n5
            5 -> (this as (OfAtLeast6<*, *, *, *, *, *, *>)).n6
            6 -> (this as (OfAtLeast7<*, *, *, *, *, *, *, *>)).n7
            7 -> (this as (OfAtLeast8<*, *, *, *, *, *, *, *, *>)).n8
            else -> (this as OfAtLeast9<*, *, *, *, *, *, *, *, *>).getOrNull(n)
        }
    } catch (e: ClassCastException) {
        val indexOutOfBoundsException = IndexOutOfBoundsException(n.toString())
        indexOutOfBoundsException.addSuppressed(e)
        throw indexOutOfBoundsException
    }
}

/**
 * Hent verdien av typen [A] fra identen.
 *
 * @return verdien av typen [A] fra identen, eller `null` hvis identen ikke
 *         inneholder en verdi av typen [A].
 */
inline fun <reified A : Comparable<A>> Ident.getOrNull(): A? {
    val n = type.bottomTypes[typeOf<A>()] ?: return null
    return getOrThrow(n) as A
}

@Deprecated("Hjelpermetode for migrering, la heller identene være typet")
inline fun <T : Ident, reified A : Comparable<A>> T.updateOrThrow(
    noinline f: (A) -> A
): T {
    val self: Ident = this
    @Suppress("UNCHECKED_CAST")
    return when (self) {
        is Empty -> throw NoSuchElementException()
        is And<*, *> -> self.updateOrThrow(f)
    } as T
}

@JvmName("get1of1")
operator fun <A : Comparable<A>> IdentIn1<A>.component1(): A = this.last

@Suppress("UNCHECKED_CAST")
@JvmName("get1of2")
operator fun <A : Comparable<A>> IdentIn2<A, *>.component1(): A =
    (this as OfAtLeast2<*, A, *>).n2

@JvmName("get2of2")
operator fun <A : Comparable<A>> IdentIn2<*, A>.component2(): A = this.last

@Suppress("UNCHECKED_CAST")
@JvmName("get1of3")
operator fun <A : Comparable<A>> IdentIn3<A, *, *>.component1(): A =
    (this as OfAtLeast3<*, A, *, *>).n3

@Suppress("UNCHECKED_CAST")
@JvmName("get2of3")
operator fun <A : Comparable<A>> IdentIn3<*, A, *>.component2(): A =
    (this as OfAtLeast2<*, A, *>).n2

@JvmName("get3of3")
operator fun <A : Comparable<A>> IdentIn3<*, *, A>.component3(): A = last

@Suppress("UNCHECKED_CAST")
@JvmName("get1of4")
operator fun <A : Comparable<A>> IdentIn4<A, *, *, *>.component2(): A =
    (this as OfAtLeast4<*, A, *, *, *>).n4

@Suppress("UNCHECKED_CAST")
@JvmName("get2of4")
operator fun <A : Comparable<A>> IdentIn4<*, A, *, *>.component3(): A =
    (this as OfAtLeast3<*, A, *, *>).n3

@Suppress("UNCHECKED_CAST")
@JvmName("get3of4")
operator fun <A : Comparable<A>> IdentIn4<*, *, A, *>.component4(): A =
    (this as OfAtLeast2<*, A, *>).n2

@JvmName("get4of4")
operator fun <A : Comparable<A>> IdentIn4<*, *, *, A>.component5(): A = last

@Suppress("UNCHECKED_CAST")
@JvmName("get1of5")
operator fun <A : Comparable<A>> IdentIn5<A, *, *, *, *>.component1(): A =
    (this as OfAtLeast5<*, A, *, *, *, *>).n5

@Suppress("UNCHECKED_CAST")
@JvmName("get2of5")
operator fun <A : Comparable<A>> IdentIn5<*, A, *, *, *>.component2(): A =
    (this as OfAtLeast4<*, A, *, *, *>).n4

@Suppress("UNCHECKED_CAST")
@JvmName("get3of5")
operator fun <A : Comparable<A>> IdentIn5<*, *, A, *, *>.component3(): A =
    (this as OfAtLeast3<*, A, *, *>).n3

@Suppress("UNCHECKED_CAST")
@JvmName("get4of5")
operator fun <A : Comparable<A>> IdentIn5<*, *, *, A, *>.component4(): A =
    (this as OfAtLeast2<*, A, *>).n2

@JvmName("get5of5")
operator fun <A : Comparable<A>> IdentIn5<*, *, *, *, A>.component5(): A = last

@Suppress("UNCHECKED_CAST")
@JvmName("get1of6")
operator fun <A : Comparable<A>> IdentIn6<A, *, *, *, *, *>.component1(): A =
    (this as OfAtLeast6<*, A, *, *, *, *, *>).n6

@Suppress("UNCHECKED_CAST")
@JvmName("get2of6")
operator fun <A : Comparable<A>> IdentIn6<*, A, *, *, *, *>.component2(): A =
    (this as OfAtLeast5<*, A, *, *, *, *>).n5

@Suppress("UNCHECKED_CAST")
@JvmName("get3of6")
operator fun <A : Comparable<A>> IdentIn6<*, *, A, *, *, *>.component3(): A =
    (this as OfAtLeast4<*, A, *, *, *>).n4

@Suppress("UNCHECKED_CAST")
@JvmName("get4of6")
operator fun <A : Comparable<A>> IdentIn6<*, *, *, A, *, *>.component4(): A =
    (this as OfAtLeast3<*, A, *, *>).n3

@Suppress("UNCHECKED_CAST")
@JvmName("get5of6")
operator fun <A : Comparable<A>> IdentIn6<*, *, *, *, A, *>.component5(): A =
    (this as OfAtLeast2<*, A, *>).n2

@JvmName("get6of6")
operator fun <A : Comparable<A>> IdentIn6<*, *, *, *, *, A>.component6(): A = last

@Suppress("UNCHECKED_CAST")
@JvmName("get1of7")
operator fun <A : Comparable<A>> IdentIn7<A, *, *, *, *, *, *>.component1(): A =
    (this as OfAtLeast7<*, A, *, *, *, *, *, *>).n7

@Suppress("UNCHECKED_CAST")
@JvmName("get2of7")
operator fun <A : Comparable<A>> IdentIn7<*, A, *, *, *, *, *>.component2(): A =
    (this as OfAtLeast6<*, A, *, *, *, *, *>).n6

@Suppress("UNCHECKED_CAST")
@JvmName("get3of7")
operator fun <A : Comparable<A>> IdentIn7<*, *, A, *, *, *, *>.component3(): A =
    (this as OfAtLeast5<*, A, *, *, *, *>).n5

@Suppress("UNCHECKED_CAST")
@JvmName("get4of7")
operator fun <A : Comparable<A>> IdentIn7<*, *, *, A, *, *, *>.component4(): A = (this as OfAtLeast4<*, A, *, *, *>).n4

@Suppress("UNCHECKED_CAST")
@JvmName("get5of7")
operator fun <A : Comparable<A>> IdentIn7<*, *, *, *, A, *, *>.component5(): A = (this as OfAtLeast3<*, A, *, *>).n3

@Suppress("UNCHECKED_CAST")
@JvmName("get6of7")
operator fun <A : Comparable<A>> IdentIn7<*, *, *, *, *, A, *>.component6(): A = (this as OfAtLeast2<*, A, *>).n2

@JvmName("get7of7")
operator fun <A : Comparable<A>> IdentIn7<*, *, *, *, *, *, A>.component7(): A = last

@Suppress("UNCHECKED_CAST")
@JvmName("get1of8")
operator fun <A : Comparable<A>> IdentIn8<A, *, *, *, *, *, *, *>.component1(): A =
    (this as OfAtLeast8<*, A, *, *, *, *, *, *, *>).n8

@Suppress("UNCHECKED_CAST")
@JvmName("get2of8")
operator fun <A : Comparable<A>> IdentIn8<*, A, *, *, *, *, *, *>.component2(): A =
    (this as OfAtLeast7<*, A, *, *, *, *, *, *>).n7

@Suppress("UNCHECKED_CAST")
@JvmName("get3of8")
operator fun <A : Comparable<A>> IdentIn8<*, *, A, *, *, *, *, *>.component3(): A =
    (this as OfAtLeast6<*, A, *, *, *, *, *>).n6

@Suppress("UNCHECKED_CAST")
@JvmName("get4of8")
operator fun <A : Comparable<A>> IdentIn8<*, *, *, A, *, *, *, *>.component4(): A =
    (this as OfAtLeast5<*, A, *, *, *, *>).n5

@Suppress("UNCHECKED_CAST")
@JvmName("get5of8")
operator fun <A : Comparable<A>> IdentIn8<*, *, *, *, A, *, *, *>.component5(): A =
    (this as OfAtLeast4<*, A, *, *, *>).n4

@Suppress("UNCHECKED_CAST")
@JvmName("get6of8")
operator fun <A : Comparable<A>> IdentIn8<*, *, *, *, *, A, *, *>.component6(): A =
    (this as OfAtLeast3<*, A, *, *>).n3

@JvmName("get7of8")
@Suppress("UNCHECKED_CAST")
operator fun <A : Comparable<A>> IdentIn8<*, *, *, *, *, *, A, *>.component7(): A =
    (this as OfAtLeast2<*, A, *>).n2

@JvmName("get8of8")
operator fun <A : Comparable<A>> IdentIn8<*, *, *, *, *, *, *, A>.component8(): A =
    last

operator fun <A> IdentType<Empty, A>.invoke(
    a: A
): And<Empty, A> where A : Comparable<A> =
    OfExactly1(this, a)

operator fun <A, B> IdentType<And<Empty, A>, B>.invoke(
    a: A, b: B
): And<And<Empty, A>, B> where A : Comparable<A>, B : Comparable<B> =
    OfExactly2(this, a, b)

operator fun <A, B, C> IdentType<Ident2<A, B>, C>.invoke(
    a: A, b: B, c: C
): And<Ident2<A, B>, C>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C> =
    OfExactly3(this, a, b, c)

operator fun <A, B, C, D> IdentType<Ident3<A, B, C>, D>.invoke(
    a: A, b: B, c: C, d: D
): And<Ident3<A, B, C>, D>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D> =
    OfExactly4(this, a, b, c, d)

operator fun <A, B, C, D, E> IdentType<Ident4<A, B, C, D>, E>.invoke(
    a: A, b: B, c: C, d: D, e: E
): And<Ident4<A, B, C, D>, E>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E> =
    OfExactly5(this, a, b, c, d, e)

operator fun <A, B, C, D, E, F> IdentType<Ident5<A, B, C, D, E>, F>.invoke(
    a: A, b: B, c: C, d: D, e: E, f: F
): And<Ident5<A, B, C, D, E>, F>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E>, F : Comparable<F> =
    OfExactly6(this, a, b, c, d, e, f)

operator fun <A, B, C, D, E, F, G> IdentType<Ident6<A, B, C, D, E, F>, G>.invoke(
    a: A, b: B, c: C, d: D, e: E, f: F, g: G
): And<Ident6<A, B, C, D, E, F>, G>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E>, F : Comparable<F>,
              G : Comparable<G> =
    OfExactly7(this, a, b, c, d, e, f, g)

operator fun <A, B, C, D, E, F, G, H> IdentType<Ident7<A, B, C, D, E, F, G>, H>.invoke(
    a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H
): And<Ident7<A, B, C, D, E, F, G>, H>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E>, F : Comparable<F>,
              G : Comparable<G>, H : Comparable<H> =
    OfExactly8(this, a, b, c, d, e, f, g, h)

/**
 * Lag en ident med type [type] og verdier [values].
 *
 * @throws IllegalArgumentException hvis [values] ikke har riktig lengde
 * @throws ClassCastException hvis elementene i [values] ikke er av riktig
 *         (erased) type iht. [type]
 */
@Suppress("UNCHECKED_CAST")
fun identWithTypeOrThrow(type: IdentType<*, *>, vararg values: Comparable<*>): Ident {
    val vals = type
        .rawTypes
        .mapIndexed { i, kClass -> kClass.cast(values[i]) }
    if (type.size != values.size) throw IllegalArgumentException("Expected ${type.size} values, got ${values.size}")
    return when (type.size) {
        0 -> Empty
        1 -> OfExactly1(
            type as IdentType<Empty, Comparable<Comparable<*>>>,
            vals[0]
        )

        2 -> OfExactly2(
            type as IdentType<Ident1<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>,
            vals[0], vals[1]
        )

        3 -> OfExactly3(
            type as IdentType<Ident2<Comparable<Comparable<*>>, Comparable<Comparable<*>>>, Comparable<Comparable<*>>>,
            vals[0], vals[1], vals[2]
        )

        4 -> OfExactly4(
            type as IdentType<Ident3<Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>>, Comparable<Comparable<*>>>,
            vals[0], vals[1], vals[2], vals[3]
        )

        5 -> OfExactly5(
            type as IdentType<Ident4<Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>>, Comparable<Comparable<*>>>,
            vals[0], vals[1], vals[2], vals[3], vals[4]
        )

        6 -> OfExactly6(
            type as IdentType<Ident5<Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>>, Comparable<Comparable<*>>>,
            vals[0], vals[1], vals[2], vals[3], vals[4], vals[5]
        )

        7 -> OfExactly7(
            type as IdentType<Ident6<Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>>, Comparable<Comparable<*>>>,
            vals[0], vals[1], vals[2], vals[3], vals[4], vals[5], vals[6]
        )

        8 -> OfExactly8(
            type as IdentType<Ident7<Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>>, Comparable<Comparable<*>>>,
            vals[0], vals[1], vals[2], vals[3], vals[4], vals[5], vals[6], vals[7]
        )

        else -> {
            OfAtLeast9(
                type as IdentType<Ident8<Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>, Comparable<Comparable<*>>>, Comparable<Comparable<*>>>,
                values as Array<Comparable<*>>
            )
        }
    }
}

inline fun <A> IdentType<Empty, A>.identOf(a: A): Ident1<A> where A : Comparable<A> = this(a)

inline fun <A, B> IdentType<Ident1<A>, B>.identOf(a: A, b: B): Ident2<A, B>
        where A : Comparable<A>, B : Comparable<B> = this(a, b)

inline fun <A, B, C> IdentType<Ident2<A, B>, C>.identOf(a: A, b: B, c: C): Ident3<A, B, C>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C> =
    this(a, b, c)

inline fun <A, B, C, D> IdentType<Ident3<A, B, C>, D>.identOf(a: A, b: B, c: C, d: D): Ident4<A, B, C, D>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>, D : Comparable<D> =
    this(a, b, c, d)

private typealias OfAtLeast1<T, A> = And<T, A>

private data class OfExactly1<A>(
    override val type: IdentType<Empty, A>, override val last: A
) : OfAtLeast1<Empty, A>() where A : Comparable<A> {
    override fun dropLast(): Empty = Empty

    @Suppress("UNCHECKED_CAST")
    override fun updateOrThrowImpl(n: Int, f: (Comparable<*>) -> Comparable<*>): Ident1<A> = when (n) {
        0 -> OfExactly1(type, f(last) as A)
        else -> throw IndexOutOfBoundsException("Index: $n, Size: $size")
    }

    override fun toArray(): Array<Comparable<*>> = arrayOf(last)

    override fun <V : Comparable<V>> appendWith(type: IdentType<Ident1<A>, V>, value: V): Ident2<A, V> =
        OfExactly2(type, last, value)

    override fun compareTo(other: And<Empty, A>): Int =
        last.compareTo((other as OfExactly1).last)

}

private sealed class OfAtLeast2<T : Ident, A, B> :
    OfAtLeast1<And<T, A>, B>() where A : Comparable<A>, B : Comparable<B> {
    abstract val n2: A

}

private data class OfExactly2<A, B>(
    override val type: IdentType<Ident1<A>, B>, override val n2: A, override val last: B
) : OfAtLeast2<Empty, A, B>() where A : Comparable<A>, B : Comparable<B> {
    override fun dropLast() = OfExactly1(type.dropLast(), n2)

    @Suppress("UNCHECKED_CAST")
    override fun updateOrThrowImpl(n: Int, f: (Comparable<*>) -> Comparable<*>) = when (n) {
        0 -> OfExactly2(type, f(n2) as A, last)
        1 -> OfExactly2(type, n2, f(last) as B)
        else -> throw IndexOutOfBoundsException("Index: $n, Size: $size")
    }

    override fun toArray(): Array<Comparable<*>> = arrayOf(n2, last)

    override fun <V : Comparable<V>> appendWith(
        type: IdentType<Ident2<A, B>, V>,
        value: V
    ): Ident3<A, B, V> = OfExactly3(type, n2, last, value)

    override fun compareTo(other: And<And<Empty, A>, B>): Int {
        val that = other as OfExactly2
        return n2.compareTo(that.n2).takeIf { it != 0 }
            ?: last.compareTo(that.last)
    }
}

private sealed class OfAtLeast3<T : Ident, A, B, C> :
    OfAtLeast2<And<T, A>, B, C>()
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C> {
    abstract val n3: A
}

private data class OfExactly3<A, B, C>(
    override val type: IdentType<Ident2<A, B>, C>,
    override val n3: A,
    override val n2: B,
    override val last: C,
) : OfAtLeast3<Empty, A, B, C>()
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C> {
    override fun dropLast(): Ident2<A, B> = OfExactly2(type.dropLast(), n3, n2)

    @Suppress("UNCHECKED_CAST")
    override fun updateOrThrowImpl(n: Int, f: (Comparable<*>) -> Comparable<*>) = when (n) {
        0 -> OfExactly3(type, f(n3) as A, n2, last)
        1 -> OfExactly3(type, n3, f(n2) as B, last)
        2 -> OfExactly3(type, n3, n2, f(last) as C)
        else -> throw IndexOutOfBoundsException("Index: $n, $size")
    }

    override fun toArray(): Array<Comparable<*>> = toArray()

    override fun <V : Comparable<V>> appendWith(
        type: IdentType<Ident3<A, B, C>, V>,
        value: V
    ): Ident4<A, B, C, V> =
        OfExactly4(type, n3, n2, last, value)

    override fun compareTo(other: And<And<And<Empty, A>, B>, C>): Int {
        val that = other as OfExactly3
        return n3.compareTo(that.n3).takeIf { it != 0 }
            ?: n2.compareTo(that.n2).takeIf { it != 0 }
            ?: last.compareTo(that.last)
    }
}

private sealed class OfAtLeast4<T : Ident, A, B, C, D> :
    OfAtLeast3<And<T, A>, B, C, D>()
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D> {
    abstract val n4: A
}

private data class OfExactly4<A, B, C, D>(
    override val type: IdentType<Ident3<A, B, C>, D>,
    override val n4: A,
    override val n3: B,
    override val n2: C,
    override val last: D
) :
    OfAtLeast4<Empty, A, B, C, D>()
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D> {
    override fun dropLast(): Ident3<A, B, C> = OfExactly3(type.dropLast(), n4, n3, n2)

    @Suppress("UNCHECKED_CAST")
    override fun updateOrThrowImpl(n: Int, f: (Comparable<*>) -> Comparable<*>) = when (n) {
        0 -> OfExactly4(type, f(n4) as A, n3, n2, last)
        1 -> OfExactly4(type, n4, f(n3) as B, n2, last)
        2 -> OfExactly4(type, n4, n3, f(n2) as C, last)
        3 -> OfExactly4(type, n4, n3, n2, f(last) as D)
        else -> throw IndexOutOfBoundsException("Index: $n, Size: $size")
    }

    override fun toArray(): Array<Comparable<*>> = toArray()

    override fun <V : Comparable<V>> appendWith(
        type: IdentType<Ident4<A, B, C, D>, V>,
        value: V
    ): Ident5<A, B, C, D, V> = OfExactly5(type, n4, n3, n2, last, value)

    override fun compareTo(other: And<And<And<And<Empty, A>, B>, C>, D>): Int {
        val that = other as OfExactly4
        return n4.compareTo(that.n4).takeIf { it != 0 }
            ?: n3.compareTo(that.n3).takeIf { it != 0 }
            ?: n2.compareTo(that.n2).takeIf { it != 0 }
            ?: last.compareTo(that.last)
    }
}

private sealed class OfAtLeast5<T : Ident, A, B, C, D, E> :
    OfAtLeast4<And<T, A>, B, C, D, E>()
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E> {
    abstract val n5: A
}

private data class OfExactly5<A, B, C, D, E>(
    override val type: IdentType<Ident4<A, B, C, D>, E>,
    override val n5: A,
    override val n4: B,
    override val n3: C,
    override val n2: D,
    override val last: E
) :
    OfAtLeast5<Empty, A, B, C, D, E>()
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E> {
    override fun <V : Comparable<V>> appendWith(
        type: IdentType<Ident5<A, B, C, D, E>, V>,
        value: V
    ): Ident6<A, B, C, D, E, V> = OfExactly6(type, n5, n4, n3, n2, last, value)

    override fun dropLast(): Ident4<A, B, C, D> = OfExactly4(type.dropLast(), n5, n4, n3, n2)

    @Suppress("UNCHECKED_CAST")
    override fun updateOrThrowImpl(n: Int, f: (Comparable<*>) -> Comparable<*>) = when (n) {
        0 -> OfExactly5(type, f(n5) as A, n4, n3, n2, last)
        1 -> OfExactly5(type, n5, f(n4) as B, n3, n2, last)
        2 -> OfExactly5(type, n5, n4, f(n3) as C, n2, last)
        3 -> OfExactly5(type, n5, n4, n3, f(n2) as D, last)
        4 -> OfExactly5(type, n5, n4, n3, n2, f(last) as E)
        else -> throw IndexOutOfBoundsException("Index: $n, Size: $size")
    }

    override fun toArray(): Array<Comparable<*>> = toArray()

    override fun compareTo(other: And<And<And<And<And<Empty, A>, B>, C>, D>, E>): Int {
        val that = other as OfExactly5
        return n5.compareTo(that.n5).takeIf { it != 0 }
            ?: n4.compareTo(that.n4).takeIf { it != 0 }
            ?: n3.compareTo(that.n3).takeIf { it != 0 }
            ?: n2.compareTo(that.n2).takeIf { it != 0 }
            ?: return last.compareTo(other.last)
    }
}

private sealed class OfAtLeast6<T : Ident, A, B, C, D, E, F> :
    OfAtLeast5<And<T, A>, B, C, D, E, F>()
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E>, F : Comparable<F> {
    abstract val n6: A
}

private data class OfExactly6<A, B, C, D, E, F>(
    override val type: IdentType<Ident5<A, B, C, D, E>, F>,
    override val n6: A,
    override val n5: B,
    override val n4: C,
    override val n3: D,
    override val n2: E,
    override val last: F
) :
    OfAtLeast6<Empty, A, B, C, D, E, F>()
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E>, F : Comparable<F> {

    override fun <V : Comparable<V>> appendWith(
        type: IdentType<Ident6<A, B, C, D, E, F>, V>,
        value: V
    ): Ident7<A, B, C, D, E, F, V> = OfExactly7(type, n6, n5, n4, n3, n2, last, value)

    @Suppress("UNCHECKED_CAST")
    override fun updateOrThrowImpl(n: Int, f: (Comparable<*>) -> Comparable<*>) = when (n) {
        0 -> OfExactly6(type, f(n6) as A, n5, n4, n3, n2, last)
        1 -> OfExactly6(type, n6, f(n5) as B, n4, n3, n2, last)
        2 -> OfExactly6(type, n6, n5, f(n4) as C, n3, n2, last)
        3 -> OfExactly6(type, n6, n5, n4, f(n3) as D, n2, last)
        4 -> OfExactly6(type, n6, n5, n4, n3, f(n2) as E, last)
        5 -> OfExactly6(type, n6, n5, n4, n3, n2, f(last) as F)
        else -> throw IndexOutOfBoundsException("Index: $n, Size: $size")
    }

    override fun toArray(): Array<Comparable<*>> = arrayOf(n6, n5, n4, n3, n2, last)

    override fun dropLast(): Ident5<A, B, C, D, E> = OfExactly5(type.dropLast(), n6, n5, n4, n3, n2)

    @Suppress("DuplicatedCode")
    override fun compareTo(other: And<And<And<And<And<And<Empty, A>, B>, C>, D>, E>, F>): Int {
        val that = other as OfExactly6
        return n6.compareTo(that.n6).takeIf { it != 0 }
            ?: n5.compareTo(that.n5).takeIf { it != 0 }
            ?: n4.compareTo(that.n4).takeIf { it != 0 }
            ?: n3.compareTo(that.n3).takeIf { it != 0 }
            ?: n2.compareTo(that.n2).takeIf { it != 0 }
            ?: return last.compareTo(other.last)
    }
}

private sealed class OfAtLeast7<T : Ident, A, B, C, D, E, F, G> :
    OfAtLeast6<And<T, A>, B, C, D, E, F, G>()
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E>, F : Comparable<F>,
              G : Comparable<G> {
    abstract val n7: A
}

private data class OfExactly7<A, B, C, D, E, F, G>(
    override val type: IdentType<Ident6<A, B, C, D, E, F>, G>,
    override val n7: A,
    override val n6: B,
    override val n5: C,
    override val n4: D,
    override val n3: E,
    override val n2: F,
    override val last: G
) :
    OfAtLeast7<Empty, A, B, C, D, E, F, G>()
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E>, F : Comparable<F>,
              G : Comparable<G> {

    override fun <V : Comparable<V>> appendWith(
        type: IdentType<Ident7<A, B, C, D, E, F, G>, V>,
        value: V
    ): Ident8<A, B, C, D, E, F, G, V> = OfExactly8(type, n7, n6, n5, n4, n3, n2, last, value)

    override fun dropLast(): Ident6<A, B, C, D, E, F> = OfExactly6(type.dropLast(), n7, n6, n5, n4, n3, n2)

    @Suppress("UNCHECKED_CAST")
    override fun updateOrThrowImpl(n: Int, f: (Comparable<*>) -> Comparable<*>) = when (n) {
        0 -> OfExactly7(type, f(n7) as A, n6, n5, n4, n3, n2, last)
        1 -> OfExactly7(type, n7, f(n6) as B, n5, n4, n3, n2, last)
        2 -> OfExactly7(type, n7, n6, f(n5) as C, n4, n3, n2, last)
        3 -> OfExactly7(type, n7, n6, n5, f(n4) as D, n3, n2, last)
        4 -> OfExactly7(type, n7, n6, n5, n4, f(n3) as E, n2, last)
        5 -> OfExactly7(type, n7, n6, n5, n4, n3, f(n2) as F, last)
        6 -> OfExactly7(type, n7, n6, n5, n4, n3, n2, f(last) as G)
        else -> throw IndexOutOfBoundsException("Index: $n, Size: $size")
    }

    override fun toArray(): Array<Comparable<*>> = arrayOf(n7, n6, n5, n4, n3, n2, last)

    @Suppress("DuplicatedCode")
    override fun compareTo(other: And<And<And<And<And<And<And<Empty, A>, B>, C>, D>, E>, F>, G>): Int {
        val that = other as OfExactly7
        return n7.compareTo(that.n7).takeIf { it != 0 }
            ?: n6.compareTo(that.n6).takeIf { it != 0 }
            ?: n5.compareTo(that.n5).takeIf { it != 0 }
            ?: n4.compareTo(that.n4).takeIf { it != 0 }
            ?: n3.compareTo(that.n3).takeIf { it != 0 }
            ?: n2.compareTo(that.n2).takeIf { it != 0 }
            ?: return last.compareTo(other.last)
    }
}

private sealed class OfAtLeast8<T : Ident, A, B, C, D, E, F, G, H> :
    OfAtLeast7<And<T, A>, B, C, D, E, F, G, H>()
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E>, F : Comparable<F>,
              G : Comparable<G>, H : Comparable<H> {
    abstract val n8: A
}

private data class OfExactly8<A, B, C, D, E, F, G, H>(
    override val type: IdentType<Ident7<A, B, C, D, E, F, G>, H>,
    override val n8: A,
    override val n7: B,
    override val n6: C,
    override val n5: D,
    override val n4: E,
    override val n3: F,
    override val n2: G,
    override val last: H,
) :
    OfAtLeast8<Empty, A, B, C, D, E, F, G, H>()
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E>, F : Comparable<F>,
              G : Comparable<G>, H : Comparable<H> {

    override fun <V : Comparable<V>> appendWith(
        type: IdentType<Ident8<A, B, C, D, E, F, G, H>, V>,
        value: V
    ): And<Ident8<A, B, C, D, E, F, G, H>, V> =
        OfAtLeast9(type, arrayOf(n8, n7, n6, n5, n4, n3, n2, last, value))

    override fun dropLast(): Ident7<A, B, C, D, E, F, G> = OfExactly7(type.dropLast(), n8, n7, n6, n5, n4, n3, n2)

    @Suppress("UNCHECKED_CAST")
    override fun updateOrThrowImpl(
        n: Int,
        f: (Comparable<*>) -> Comparable<*>
    ) = when (n) {
        0 -> OfExactly8(type, f(n8) as A, n7, n6, n5, n4, n3, n2, last)
        1 -> OfExactly8(type, n8, f(n7) as B, n6, n5, n4, n3, n2, last)
        2 -> OfExactly8(type, n8, n7, f(n6) as C, n5, n4, n3, n2, last)
        3 -> OfExactly8(type, n8, n7, n6, f(n5) as D, n4, n3, n2, last)
        4 -> OfExactly8(type, n8, n7, n6, n5, f(n4) as E, n3, n2, last)
        5 -> OfExactly8(type, n8, n7, n6, n5, n4, f(n3) as F, n2, last)
        6 -> OfExactly8(type, n8, n7, n6, n5, n4, n3, f(n2) as G, last)
        7 -> OfExactly8(type, n8, n7, n6, n5, n4, n3, n2, f(last) as H)
        else -> throw IndexOutOfBoundsException("Index: $n, Size: $size")
    }

    override fun toArray(): Array<Comparable<*>> = arrayOf(n8, n7, n6, n5, n4, n3, n2, last)

    override fun compareTo(other: Ident8<A, B, C, D, E, F, G, H>): Int {
        val that = other as OfExactly8
        @Suppress("DuplicatedCode")
        return n8.compareTo(that.n8).takeIf { it != 0 }
            ?: n7.compareTo(that.n7).takeIf { it != 0 }
            ?: n6.compareTo(that.n6).takeIf { it != 0 }
            ?: n5.compareTo(that.n5).takeIf { it != 0 }
            ?: n4.compareTo(that.n4).takeIf { it != 0 }
            ?: n3.compareTo(that.n3).takeIf { it != 0 }
            ?: n2.compareTo(that.n2).takeIf { it != 0 }
            ?: return last.compareTo(other.last)
    }
}

@Suppress("UNCHECKED_CAST")
private data class OfAtLeast9<T : Ident, A, B, C, D, E, F, G, H>(
    override val type: IdentType<And<And<And<And<And<And<And<T, A>, B>, C>, D>, E>, F>, G>, H>,
    private val values: Array<Comparable<*>>,
) :
    OfAtLeast8<T, A, B, C, D, E, F, G, H>()
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C>,
              D : Comparable<D>, E : Comparable<E>, F : Comparable<F>,
              G : Comparable<G>, H : Comparable<H> {
    private fun <J : Comparable<J>> n9(): J = values[values.size - 9] as J
    override val n8: A get() = values[values.size - 8] as A
    override val n7: B get() = values[values.size - 7] as B
    override val n6: C get() = values[values.size - 6] as C
    override val n5: D get() = values[values.size - 5] as D
    override val n4: E get() = values[values.size - 4] as E
    override val n3: F get() = values[values.size - 3] as F
    override val n2: G get() = values[values.size - 2] as G
    override val last: H get() = values[values.size - 1] as H

    init {
        require(values.size >= 9)
    }

    override fun dropLast(): And<And<And<And<And<And<And<T, A>, B>, C>, D>, E>, F>, G> {
        val tpe = type.dropLast()
        return if (size > 9) {
            OfAtLeast9(
                tpe as IdentType<And<And<And<And<And<And<And<T, Comparable<Comparable<*>>>, A>, B>, C>, D>, E>, F>, G>,
                values.dropLast(1).toTypedArray()
            ) as And<And<And<And<And<And<And<T, A>, B>, C>, D>, E>, F>, G>
        } else if (size == 9) {
            OfExactly8(
                tpe as IdentType<And<And<And<And<And<And<And<Empty, Comparable<Comparable<*>>>, A>, B>, C>, D>, E>, F>, G>,
                n9(), n8, n7, n6, n5, n4, n3, n2
            ) as And<And<And<And<And<And<And<T, A>, B>, C>, D>, E>, F>, G>
        } else {
            throw IllegalStateException()
        }
    }

    override fun updateOrThrowImpl(
        n: Int,
        f: (Comparable<*>) -> Comparable<*>
    ) = OfAtLeast9(type, values.copyOf().also { it[n] = f(it[n]) })

    override fun toArray(): Array<Comparable<*>> = values.copyOf()

    override fun <V : Comparable<V>> appendWith(
        type: IdentType<And<And<And<And<And<And<And<And<T, A>, B>, C>, D>, E>, F>, G>, H>, V>,
        value: V
    ): And<And<And<And<And<And<And<And<And<T, A>, B>, C>, D>, E>, F>, G>, H>, V> =
        OfAtLeast9(type, values + value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return values.contentEquals((other as? OfAtLeast9<*, *, *, *, *, *, *, *, *>)?.values)
    }

    override fun hashCode(): Int {
        return values.contentHashCode()
    }

    fun getOrNull(n: Int): Comparable<*> = try {
        values[values.size - n]
    } catch (e: IndexOutOfBoundsException) {
        throw IndexOutOfBoundsException("$n")
    }

    override fun compareTo(
        other: And<And<And<And<And<And<And<And<T, A>, B>, C>, D>, E>, F>, G>, H>
    ): Int {
        val that = other as OfAtLeast9
        val sizeCmp = values.size.compareTo(that.values.size)
        if (sizeCmp != 0) return sizeCmp
        for (i in values.indices) {
            val cmp = compareValues<Comparable<*>>(
                values[i],
                that.values[i]
            )
            if (cmp != 0) return cmp
        }
        return 0
    }
}