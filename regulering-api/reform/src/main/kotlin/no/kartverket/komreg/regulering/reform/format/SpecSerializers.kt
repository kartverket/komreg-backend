@file:OptIn(ExperimentalSerializationApi::class)

package no.kartverket.komreg.regulering.reform.format

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Serializer for felter som kan ha flere JSON-former. Fungerer bare med [Json]. */
internal abstract class JsonShapeSerializer<T : Any>(private val serialName: String) : KSerializer<T> {
    override val descriptor: SerialDescriptor = SerialDescriptor(serialName, JsonElement.serializer().descriptor)

    protected abstract fun fromJson(json: Json, element: JsonElement): T

    protected abstract fun toJson(json: Json, value: T): JsonElement

    override fun deserialize(decoder: Decoder): T {
        val input = decoder as? JsonDecoder ?: throw SerializationException("$serialName kan bare dekodes fra JSON")
        return fromJson(input.json, input.decodeJsonElement())
    }

    override fun serialize(encoder: Encoder, value: T) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("$serialName kan bare kodes til JSON")
        output.encodeJsonElement(toJson(output.json, value))
    }
}

private fun JsonElement.segment(field: String): JsonPrimitive =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }
        ?: throw SerializationException("Segmentene i $field må være strenger eller tall, fikk $this")

private fun JsonArray.segments(field: String): List<JsonPrimitive> = map { it.segment(field) }

internal object PathSpecSerializer : JsonShapeSerializer<PathSpec>("PathSpec") {
    override fun fromJson(json: Json, element: JsonElement): PathSpec = when (element) {
        is JsonArray -> PathSpec.Path(element.segments("key"))
        else -> PathSpec.Single(element.segment("key"))
    }

    override fun toJson(json: Json, value: PathSpec): JsonElement = when (value) {
        is PathSpec.Single -> value.value
        is PathSpec.Path -> JsonArray(value.segments)
    }
}

internal object KeySpecSerializer : JsonShapeSerializer<KeySpec>("KeySpec") {
    override fun fromJson(json: Json, element: JsonElement): KeySpec =
        KeySpec(
            (element as? JsonArray)?.segments("mål")
                ?: throw SerializationException("Et mål må være en array av segmenter, fikk $element"),
        )

    override fun toJson(json: Json, value: KeySpec): JsonElement = JsonArray(value.segments)
}

internal object SourceSpecSerializer : JsonShapeSerializer<SourceSpec>("SourceSpec") {
    override fun fromJson(json: Json, element: JsonElement): SourceSpec = when (element) {
        is JsonObject -> json.decodeFromJsonElement(SourceSpec.Full.serializer(), element)
        else -> SourceSpec.Bare(json.decodeFromJsonElement(PathSpecSerializer, element))
    }

    override fun toJson(json: Json, value: SourceSpec): JsonElement = when (value) {
        is SourceSpec.Bare -> json.encodeToJsonElement(PathSpecSerializer, value.key)
        is SourceSpec.Full -> json.encodeToJsonElement(SourceSpec.Full.serializer(), value)
    }
}

internal object IntoSpecSerializer : JsonContentPolymorphicSerializer<IntoSpec>(IntoSpec::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<IntoSpec> {
        val fields = (element as? JsonObject)?.keys
            ?: throw SerializationException("into må være et objekt, fikk $element")
        return when {
            "existing" in fields && "new" in fields ->
                throw SerializationException("into kan ikke ha både existing og new")
            "existing" in fields -> IntoSpec.Existing.serializer()
            "new" in fields -> IntoSpec.New.serializer()
            else -> throw SerializationException("into må ha enten existing eller new")
        }
    }
}
