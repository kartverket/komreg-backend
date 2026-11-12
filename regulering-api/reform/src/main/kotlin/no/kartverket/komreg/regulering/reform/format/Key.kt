package no.kartverket.komreg.regulering.reform.format

import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.identWithTypeOrThrow

/**
 * Nøkkel i resolveren: en heterogen snoc-liste av segmenter fra roten. Konteksten til en node er nøkkelen til
 * forelderen, og et barn er `Snoc(kontekst, kind, verdi)`.
 */
sealed interface Key {
    val size: Int

    /** Kinden til siste segment, eller `null` for roten. */
    val kind: SegmentKind<*>?

    data object Empty : Key {
        override val size: Int get() = 0
        override val kind: SegmentKind<*>? get() = null
    }

    data class Snoc(val init: Key, override val kind: SegmentKind<*>, val value: Any) : Key {
        init {
            require(kind.parent == init.kind) { "Kinden $kind har forelder ${kind.parent}, ikke ${init.kind}" }
            require(kind.type.isInstance(value)) { "Verdien $value er ikke en ${kind.type.simpleName}" }
        }

        override val size: Int get() = init.size + 1

        override fun toString(): String = segments().joinToString("/", "[", "]") { (kind, value) -> "$kind=$value" }
    }
}

fun Key.snoc(kind: SegmentKind<*>, value: Any): Key.Snoc = Key.Snoc(this, kind, value)

/** Segmentene fra roten. */
fun Key.segments(): List<Pair<SegmentKind<*>, Any>> = when (this) {
    Key.Empty -> emptyList()
    is Key.Snoc -> init.segments() + (kind to value)
}

/** Nøkkelen selv og så foreldrene, til og med første segment. */
fun Key.ancestorsAndSelf(): Sequence<Key.Snoc> = generateSequence(this as? Key.Snoc) { it.init as? Key.Snoc }

/**
 * Identene nøkkelen svarer til, med identtypen registeret gir for kind-stien. Én ident for alle kinds unntatt
 * `parcel`, som gir to. Rekkefølgen er stabil, slik at kilde og mål kan pares etter indeks.
 */
fun Key.Snoc.toIdents(registry: SegmentRegistry): List<Ident.NonEmpty> {
    val type = registry.identType(kind)
    val combinations = segments().fold(listOf(emptyList<Comparable<*>>())) { acc, (kind, value) ->
        acc.flatMap { prefix -> kind.expand(value).map { prefix + it } }
    }
    return combinations.map { identWithTypeOrThrow(type, *it.toTypedArray()) as Ident.NonEmpty }
}
