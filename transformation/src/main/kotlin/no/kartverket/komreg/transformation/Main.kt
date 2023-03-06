package no.kartverket.komreg.transformation

import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.data.RawData
import no.kartverket.komreg.core.data.Transformation
import no.kartverket.komreg.core.data.Transformed
import no.kartverket.komreg.core.data.Validated
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Matrikkelnummer

suspend fun main() {
    val validationRules = ValidateRaw(
        rules = listOf(
            ::validMatrikkelNummer,
        ),
    )
    val transformationRules = TransformationRules(
        transformations = listOf(
            TransformGardsnummer(1, 10),
        ),
    )
    val transformationExecution = TransformationExecution<Matrikkelnummer>()

    executeSimpleRun(validationRules, transformationRules, transformationExecution)
}

val mockDatabase = listOf(
    Matrikkelnummer(1, 1, 1, 1, 1),
    Matrikkelnummer(1, 2, 1, 1, 1),
    Matrikkelnummer(1, 3, 1, 1, 1),
    Matrikkelnummer(1, 4, 1, 1, 1),
    Matrikkelnummer(1, 5, 1, 1, 1),
    Matrikkelnummer(1, 6, 1, 1, 1),
    Matrikkelnummer(1, 6, 2, 1, 1),
    Matrikkelnummer(1, 6, 3, 1, 1),
)

suspend fun executeSimpleRun(
    rawValidationRules: ValidateRaw<Matrikkelnummer>,
    transformationRules: TransformationRules<Matrikkelnummer>,
    transformationExecution: TransformationExecution<Matrikkelnummer>,
): List<Transformed<Matrikkelnummer>> {
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.load("reference-dev.conf")
        }
    }
    val entitySources = EntitySourceManager(bootContext)
    val data = entitySources.buildMatrikkelnummerFlow()
    val result = data.take(1000)
        .map { rawValidationRules.rawToValidated(it) }
        .onEach { println(it) }
        .map { transformationRules.validToTransformation(it) }
        .onEach { println(it) }
        .map { transformationExecution.transformData(it) }
        .onEach { println(it) }
        .toList()

    entitySources.buildEntityFlow().onEach { println(it) }.toList()

    return result
}

interface TransformationAction<T> {
    fun transform(transform: Transformation<T>): Transformation<T>
}

@Serializable
data class TransformGardsnummer(val number: Int, val newNumber: Int) : TransformationAction<Matrikkelnummer> {
    override fun transform(transform: Transformation<Matrikkelnummer>): Transformation<Matrikkelnummer> {
        return when (transform) {
            is Transformation.Invalid -> transform
            is Transformation.NoOp, is Transformation.Transform -> if (transform._data.gardsnummer.value == number) {
                Transformation.Transform(
                    transform._data,
                    listOf { it.copy(gardsnummer = Matrikkelnummer.Gardsnummer(newNumber)) },
                )
            } else {
                Transformation.NoOp(transform._data)
            }
        }
    }
}

class TransformationExecution<T : Any> {
    fun transformData(transformation: Transformation<T>): Transformed<T> {
        return when (transformation) {
            is Transformation.Invalid -> Transformed.Invalid(transformation.data)
            is Transformation.NoOp -> Transformed.Data(transformation.data)
            is Transformation.Transform -> Transformed.Data(
                transformation.transformations.fold(transformation.data) { acc, function ->
                    println("Running transformation")
                    function(acc)
                },
            )
        }
    }
}

class TransformationRules<T : Any>(
    val transformations: List<TransformationAction<T>>,
) {
    fun validToTransformation(
        validated: Validated<T>,
    ): Transformation<T> = when (validated) {
        is Validated.Invalid -> Transformation.Invalid(validated.data)
        is Validated.Valid -> transformations.fold(Transformation.NoOp(validated.data) as Transformation<T>) { acc, transformationAction ->
            transformationAction.transform(acc)
        }
    }
}

class ValidateRaw<T : Any>(
    private val rules: List<(Validated<T>) -> Validated<T>>,
) {
    fun rawToValidated(raw: RawData<T>): Validated<T> {
        return rules.fold(Validated.Valid(raw.data) as Validated<T>) { acc, function -> function(acc) }
    }
}

fun validMatrikkelNummer(input: Validated<Matrikkelnummer>): Validated<Matrikkelnummer> = when (input) {
    is Validated.Invalid -> input
    is Validated.Valid -> if (input.data.gardsnummer.value != 22) {
        Validated.Valid(input.data, input.validatedWith + "Invalid gardsnummer")
    } else {
        Validated.Invalid(input.data, "Invalid gardsnummer")
    }
}

suspend fun getAllKommuner(): List<Kommune> {
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.load("reference-dev.conf")
        }
    }
    val entitySources = EntitySourceManager(bootContext)
    val rawKommuner = entitySources.buildKommuneFlow().toList()
    val kommuner = entitySources.makeKommuneListFromRawDataKommuneList(rawKommuner)

    return kommuner
}

suspend fun getAllFylker(): List<Fylke> {
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.load("reference-dev.conf")
        }
    }
    val entitySources = EntitySourceManager(bootContext)
    val rawFylker = entitySources.buildFylkeFlow().toList()
    val fylker = entitySources.makeFylkeListFromRawDataFylkeList(rawFylker)

    return fylker
}
