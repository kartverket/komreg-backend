package no.kartverket.komreg.parameter.data

import no.kartverket.komreg.integration.spi.EmptyIdentType
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.IdentOrEmptyType
import no.kartverket.komreg.integration.spi.IdentType
import no.kartverket.komreg.integration.spi.identTypeFromKotlinTypes
import no.kartverket.komreg.integration.spi.identWithTypeOrThrow
import no.kartverket.komreg.parameter.data.Tuple.Ap
import kotlin.reflect.full.starProjectedType

sealed interface Tuple {
    object Empty : Tuple {
        override val init: Tuple?
            get() = null
        override val last: Any?
            get() = null
    }

    sealed interface NonEmpty : Tuple {
        override val init: Tuple
    }

    interface Ap<Init : Tuple, Last> : NonEmpty {
        override val init: Init
        override val last: Last
    }

    val init: Tuple?
    val last: Any?


}


val Tuple.length : Int
    get() = when (this) {
        is Ap<*, *> -> 1 + init.length
        is Tuple.Empty -> 0
    }

fun Tuple.asSequenceReversed(): Sequence<Any?> = sequence {
    var current: Tuple = this@asSequenceReversed
    while (current is Ap<*, *>) {
        yield(current.last)
        current = current.init
    }
}
fun Tuple.toMutableList(): List<Any?> = asSequenceReversed().toMutableList().apply { reverse() }
fun Tuple.toList(): List<Any?> = asSequenceReversed().toList().asReversed()


fun Ident.toTuple(): Tuple = when (val ident = this) {
    Ident.Empty -> Tuple.Empty
    is Ident.And<*, *> -> ident.dropLast().toTuple().append(ident.last)
}

suspend fun Tuple.toIdentType(): IdentOrEmptyType<*> {
    val types = asSequenceReversed().map {
        val kclass = it?.let { it::class } ?: Any::class
        require(kclass.typeParameters.isEmpty()) {
            "Parameterized types are not supported: ${kclass.qualifiedName}"
        }
        kclass.starProjectedType
    }.toList().reversed()

    return if (types.isEmpty()) {
        EmptyIdentType
    } else {
        identTypeFromKotlinTypes(types.first(), *types.drop(1).toTypedArray())
    }
}

suspend fun Tuple.toIdent(): Ident {
    return when(val identType = toIdentType()) {
        EmptyIdentType -> Ident.Empty
        is IdentType<*, *> -> identWithTypeOrThrow(
            identType,
            *asSequenceReversed()
                .map { it as Comparable<*> }
                .toMutableList()
                .apply { reverse() }
                .toTypedArray()
        )
    }
}
