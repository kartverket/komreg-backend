@file:OptIn(ExperimentalSerializationApi::class)

package no.kartverket.komreg.regulering.reform.format

import kotlinx.datetime.LocalDate
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * En reform slik frontend skriver og lagrer den. Speiler JSON-formatet 1:1. Segmentverdier og payload tolkes
 * ikke her, men i resolve via [SegmentRegistry]. Se `docs/reform-format.md`. Begrunnelsene bak
 * formatet står i `docs/reform-format-design.md`.
 *
 * Specen har ingen `id`. Identiteten er ressursen i regulering-api.
 */
@Serializable
data class ReformSpec(
    @EncodeDefault val format: Int = 1,
    val navn: String,
    val ikrafttredelse: LocalDate,
    val operasjoner: List<OpSpec>,
)

/** En node i reformtreet. Barn ligger alltid i `then`, og er relative til nøkkelen til noden. */
@Serializable
@JsonClassDiscriminator("op")
sealed interface OpSpec {
    val kind: String

    /** Ingen endring på [key]. Bare kontekst for barna. */
    @Serializable
    @SerialName("adjust")
    data class Adjust(
        override val kind: String,
        val key: PathSpec,
        val then: List<OpSpec> = emptyList(),
    ) : OpSpec

    /** Samme entitet får ny nøkkel [to]. Etterkommere uten egen regel følger med. */
    @Serializable
    @SerialName("move")
    data class Move(
        override val kind: String,
        val key: PathSpec,
        val to: KeySpec,
        val then: List<OpSpec> = emptyList(),
    ) : OpSpec

    /** Kildene i [from] utgår og peker på målet i [into]. */
    @Serializable
    @SerialName("merge")
    data class Merge(
        override val kind: String,
        val from: List<SourceSpec>,
        val into: IntoSpec,
    ) : OpSpec

    /** [key] deles i [into]. `role` på delene avgjør hva som skjer med kilden. `into: []` betyr at kilden utgår uten etterfølger. */
    @Serializable
    @SerialName("split")
    data class Split(
        override val kind: String,
        val key: PathSpec,
        val into: List<PartSpec>,
        val then: List<OpSpec> = emptyList(),
    ) : OpSpec

    /** Ny entitet. Uten [derivedFrom] har den ingen kilde. Med [derivedFrom] avledes den fra en kilde som ikke endres av noden. */
    @Serializable
    @SerialName("create")
    data class Create(
        override val kind: String,
        val key: PathSpec,
        val derivedFrom: KeySpec? = null,
        val payload: JsonObject? = null,
        val then: List<OpSpec> = emptyList(),
    ) : OpSpec
}

/**
 * Pivot (`key` og elementene i `merge.from`), relativ til konteksten. En skalar er ett segment av typen `kind`.
 * En array er en sti av segmenter som slutter på `kind`.
 */
@Serializable(PathSpecSerializer::class)
sealed interface PathSpec {
    val segments: List<JsonPrimitive>

    data class Single(val value: JsonPrimitive) : PathSpec {
        override val segments: List<JsonPrimitive> get() = listOf(value)
    }

    data class Path(override val segments: List<JsonPrimitive>) : PathSpec
}

/** Mål (`to`, `existing`, `new`): absolutt array av segmenter fra roten. */
@Serializable(KeySpecSerializer::class)
data class KeySpec(val segments: List<JsonPrimitive>)

/** Et element i `merge.from`: en pivot, eller `{"key": pivot, "then": [...]}`. */
@Serializable(SourceSpecSerializer::class)
sealed interface SourceSpec {
    val key: PathSpec
    val then: List<OpSpec>

    data class Bare(override val key: PathSpec) : SourceSpec {
        override val then: List<OpSpec> get() = emptyList()
    }

    @Serializable
    data class Full(override val key: PathSpec, override val then: List<OpSpec> = emptyList()) : SourceSpec
}

/** Målet i `merge`: en eksisterende nøkkel, eller en ny nøkkel med payload. */
@Serializable(IntoSpecSerializer::class)
sealed interface IntoSpec {
    val then: List<OpSpec>

    @Serializable
    data class Existing(val existing: KeySpec, override val then: List<OpSpec> = emptyList()) : IntoSpec

    @Serializable
    data class New(
        val new: KeySpec,
        val payload: JsonObject? = null,
        override val then: List<OpSpec> = emptyList(),
    ) : IntoSpec
}

/** En del i `split`: en ny del (med nøkkel og eventuell payload), eller en eksisterende etterfølger. */
@Serializable(PartSpecSerializer::class)
sealed interface PartSpec {
    val then: List<OpSpec>

    /** Høyst én del i et `split` har rolle. */
    @Serializable
    data class New(
        val new: KeySpec,
        val payload: JsonObject? = null,
        val role: NewRole? = null,
        override val then: List<OpSpec> = emptyList(),
    ) : PartSpec

    @Serializable
    data class Existing(
        val existing: KeySpec,
        val role: ExistingRole,
        override val then: List<OpSpec> = emptyList(),
    ) : PartSpec
}

@Serializable
enum class NewRole {
    /** Delen arver kildens ID (kilden flyttes hit). */
    @SerialName("identity")
    IDENTITY,

    /** Kilden utgår og peker på denne delen. */
    @SerialName("successor")
    SUCCESSOR,
}

@Serializable
enum class ExistingRole {
    /** Kilden utgår og peker på denne delen. */
    @SerialName("successor")
    SUCCESSOR,
}
