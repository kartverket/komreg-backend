package no.kartverket.komreg.api

import kotlinx.serialization.Serializable

@Serializable
data class Ruleset(
    val gaardsnummer: List<GaardsnummerRule>,
)

@Serializable
data class GaardsnummerRule(val start: Int, val end: Int, val increase: Int)

@Serializable
data class TransformedJson(val rule: String, val result: String)

@Serializable
data class ErrorsJson(val entry: String, val errors: List<String>)

@Serializable
data class Result(val transformed: List<TransformedJson>, val errors: List<ErrorsJson>)
