package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.And
import kotlin.reflect.KType

interface Transformation<T> {

    sealed interface Filter<in T> {
        object Any : Filter<kotlin.Any>
        data class AllOf<in T>(val map: Map<KType, Filter<*>>) : Filter<T>
    }

    sealed interface Result<out T> {
        object Identity : Result<Nothing>
        data class Increment<out T>(val value: Long) : Result<T>
        data class Set<T>(val value: T) : Result<T>
        data class Ambiguous<out T>(val values: Map<out T, Any>) : Result<T>
        data class Unsatisfied<out T>(val value: T) : Result<T>
    }

    companion object {
        infix fun <T, U : T> Filter<T>.or(other: Filter<T>): Filter<U> = TODO()
        infix fun <T, U> Filter<T>.and(other: Filter<U>): Filter<And<T, U>> = TODO()
    }

    val filter: Filter<T>
}
