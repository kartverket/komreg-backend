package no.kartverket.komreg.core.data

// data = Raw<Data>
// Raw + validationRules = Validated<Data>
// Validated + transformations = Transformation<Data + Rules>
// Transformation<Data + Rules> + fun<execute> = TransformedData<Data + Rules + Result>

data class RawData<out T : Any>(val data: T)

sealed class Validated<out T> {
    data class Valid<T>(val data: T, val validatedWith: List<String> = emptyList()) : Validated<T>()
    data class Invalid<T>(val data: T, val error: String) : Validated<T>()
}

sealed class Transformation<out T>(val _data: T) {
    data class Transform<T>(val data: T, val transformations: List<(T) -> T>) : Transformation<T>(data)
    data class NoOp<T>(val data: T) : Transformation<T>(data)
    data class Invalid<T>(val data: T) : Transformation<T>(data)
}

sealed class Transformed<out T> {
    data class Data<T>(val data: T) : Transformed<T>()
    data class Invalid<T>(val data: T) : Transformed<T>()
}
