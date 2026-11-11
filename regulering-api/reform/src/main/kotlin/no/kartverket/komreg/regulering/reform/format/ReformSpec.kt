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
 * ikke her, men i resolve via [SegmentRegistry]. Se `docs/reform-format.md`.
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

    /** [key] deles i [into]. `into: []` betyr oppløsning. */
    @Serializable
    @SerialName("split")
    data class Split(
        override val kind: String,
        val key: PathSpec,
        val into: List<PartSpec>,
        val then: List<OpSpec> = emptyList(),
    ) : OpSpec

    /** Ny entitet uten kilde. */
    @Serializable
    @SerialName("create")
    data class Create(
        override val kind: String,
        val key: PathSpec,
        val payload: JsonObject,
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

/** Mål (`to`, `into.existing`, `into.new`): absolutt array av segmenter fra roten. */
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

/** En del i `split`. Høyst én del har [role]. */
@Serializable
data class PartSpec(
    val to: KeySpec,
    val payload: JsonObject? = null,
    val role: Role? = null,
) {
    @Serializable
    enum class Role {
        /** Delen arver kildens ID (kilden flyttes hit). */
        @SerialName("identity")
        IDENTITY,

        /** Kilden utgår og peker på denne delen. */
        @SerialName("successor")
        SUCCESSOR,
    }
}
