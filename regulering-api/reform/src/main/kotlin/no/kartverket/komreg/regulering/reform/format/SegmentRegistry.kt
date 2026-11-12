package no.kartverket.komreg.regulering.reform.format

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.IdentOrEmptyType
import no.kartverket.komreg.integration.spi.IdentType
import no.kartverket.komreg.integration.spi.Payload
import no.kartverket.komreg.integration.spi.identTypeFromKotlinTypes
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.cast

/** Feil ved dekoding av et segment eller en payload. Uten JSON-sti; den legges på i resolve. */
sealed interface SegmentErr {
    val kind: String
    val message: String

    data class InvalidValue(override val kind: String, val value: JsonPrimitive, val reason: String) : SegmentErr {
        override val message get() = "Ugyldig verdi $value for $kind: $reason"
    }

    data class PayloadNotAllowed(override val kind: String) : SegmentErr {
        override val message get() = "$kind tar ikke payload"
    }

    data class InvalidPayload(override val kind: String, val reason: String) : SegmentErr {
        override val message get() = "Ugyldig payload for $kind: $reason"
    }
}

/** Payload for en kind som kan opprettes: JSON-typen og mappingen til core-typen. */
class PayloadKind<P : Any>(
    val serializer: KSerializer<P>,
    private val toCore: (P, ikrafttredelse: LocalDate) -> Either<String, Payload>,
) {
    fun decode(json: JsonObject, ikrafttredelse: LocalDate): Either<String, Payload> =
        Either.catchOrThrow<IllegalArgumentException, P> { Json.decodeFromJsonElement(serializer, json) }
            .mapLeft { it.message ?: "Kunne ikke lese payload" }
            .flatMap { toCore(it, ikrafttredelse) }
}

/**
 * Et segment i en nøkkel. Hver kind har én fast [parent], så kinden avgjør hele stien fra roten, og dermed
 * identtypen (se [SegmentRegistry.identType]).
 *
 * @param componentTypes typene kinden bidrar med i `Ident`, i rekkefølge.
 * @param decoder dekoder én JSON-verdi til domenetypen [T]. Unntak fra domenetypen blir [SegmentErr.InvalidValue].
 * @param expander gir komponentene i `Ident` for en verdi, én liste per ident. Én for alle kinds unntatt
 *   `parcel`, som gir to (bnr 0 og bnr 1).
 */
class SegmentKind<T : Any>(
    val name: String,
    val type: KClass<T>,
    val parent: SegmentKind<*>?,
    val componentTypes: List<KType>,
    private val decoder: Raise<String>.(JsonPrimitive) -> T,
    private val expander: (T) -> List<List<Comparable<*>>>,
    val payload: PayloadKind<*>? = null,
) {
    /** Kindene fra roten til og med denne. */
    val path: List<SegmentKind<*>> = parent?.path.orEmpty() + this

    fun decode(value: JsonPrimitive): Either<SegmentErr.InvalidValue, T> =
        either<String, T> {
            try {
                decoder(value)
            } catch (e: IllegalArgumentException) {
                raise(e.message ?: "ugyldig verdi")
            }
        }.mapLeft { SegmentErr.InvalidValue(name, value, it) }

    /** Komponentene [value] bidrar med i `Ident`, én liste per ident. */
    fun expand(value: Any): List<List<Comparable<*>>> = expander(type.cast(value))

    fun decodePayload(json: JsonObject, ikrafttredelse: LocalDate): Either<SegmentErr, Payload> =
        payload?.decode(json, ikrafttredelse)?.mapLeft { SegmentErr.InvalidPayload(name, it) }
            ?: SegmentErr.PayloadNotAllowed(name).left()

    override fun toString(): String = name
}

/**
 * Kindene formatet kjenner, og identtypen for hver kind-sti. Lag med [of] eller [v1].
 */
class SegmentRegistry private constructor(
    val kinds: List<SegmentKind<*>>,
    private val identTypes: Map<SegmentKind<*>, IdentType<*, *>>,
) {
    private val byName = kinds.associateBy { it.name }
    private val byComponentTypes = identTypes.entries.associate { (kind, type) -> type.types to kind }

    operator fun get(name: String): SegmentKind<*>? = byName[name]

    /** Identtypen for kind-stien som slutter på [kind]. */
    fun identType(kind: SegmentKind<*>): IdentType<*, *> =
        identTypes[kind] ?: throw IllegalArgumentException("Kinden $kind er ikke i registeret")

    /** Kinden med identtypen [type], hvis den finnes. */
    fun kindOf(type: IdentOrEmptyType<*>): SegmentKind<*>? = byComponentTypes[type.types]

    fun kindOf(ident: Ident): SegmentKind<*>? = kindOf(ident.type)

    companion object {
        suspend fun of(kinds: List<SegmentKind<*>>): SegmentRegistry {
            val duplicates = kinds.groupBy { it.name }.filterValues { it.size > 1 }.keys
            require(duplicates.isEmpty()) { "Kinds med samme navn: $duplicates" }
            kinds.forEach { kind ->
                require(kind.parent == null || kinds.any { it === kind.parent }) {
                    "Forelderen til $kind (${kind.parent}) er ikke i registeret"
                }
            }
            val identTypes = kinds.associateWith { kind ->
                val types = kind.path.flatMap { it.componentTypes }
                identTypeFromKotlinTypes(types.first(), *types.drop(1).toTypedArray())
            }
            val sameType = identTypes.entries.groupBy({ it.value.types }, { it.key }).filterValues { it.size > 1 }
            require(sameType.isEmpty()) { "Kinds med samme identtype: ${sameType.values}" }
            return SegmentRegistry(kinds, identTypes)
        }
    }
}
