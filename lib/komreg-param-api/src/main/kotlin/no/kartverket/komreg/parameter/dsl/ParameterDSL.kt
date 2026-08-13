package no.kartverket.komreg.parameter.dsl

import arrow.core.EitherNel
import arrow.core.raise.either
import no.kartverket.komreg.parameter.*
import no.kartverket.komreg.parameter.data.*
import no.kartverket.komreg.parameter.data.HList.Snoc
import no.kartverket.komreg.parameter.dsl.ParameterDSL.Marker
import no.kartverket.komreg.parameter.intermediate.Intermediate

@Marker
sealed class ParameterDSL<KI : HList>(
    protected val init: KI,
    protected val parametersBuilder: MutableList<Intermediate.Applied<*, *>>
) {
    context(type: DomainType<KL>)
    fun <KL : Any> move(
        from: KL, to: Snoc<KI, KL>,
        location: FileLocation = FileLocation(1)
    ) {
        addParameter(Intermediate.Move(init * from, to), location)
    }

    context(type: DomainType<KL>)
    private fun <KL : Any> addParameter(
        parameter: Intermediate<KI, KL>,
        location: FileLocation
    ) {
        parametersBuilder.add(
            Intermediate.Applied(
                parameter, type, hashSetOf(location)
            )
        )
    }

    context(type: EnumerableType<KL>)
    fun <KL : Any> moveRange(
        fromStart: KL, count: Int,
        toStart: Snoc<KI, KL>,
        location: FileLocation = FileLocation(1)
    ) {
        addParameter(
            Intermediate.MoveRange(init * fromStart, count, toStart, type.enumerator),
            location
        )
    }

    context(type: DomainType<KL>)
    fun <KL : Any> split(
        from: KL,
        location: FileLocation = FileLocation(1),
        block: SplitDSL<KI, KL>.() -> Unit
    ) {
        addParameter(Intermediate.Split(init * from), location)
        val splitDSL = SplitDSL(init * from, parametersBuilder)
        block(splitDSL)
    }

    @DslMarker
    annotation class Marker
}

@Marker
sealed interface CreateDSL<KI : HList> {
    context(type: DomainType<KL>)
    fun <KL : Any> create(
        `as`: KL,
        from: Snoc<KI, KL>,
        location: FileLocation = FileLocation(1),
        block: CreateDSL<Snoc<KI, KL>>.() -> Unit = {}
    )

    context(type: KeyCombinableType<KL, Data>)
    fun <KL : Any, Data : Any> create(
        `as`: KL,
        from: Snoc<KI, KL>,
        patchData: Data,
        block: CreateDSL<Snoc<KI, KL>>.() -> Unit = {},
        location: FileLocation = FileLocation(1)
    )

    context(type: KeyCombinableType<KL, Data>)
    fun <KL : Any, Data : Any> merge(
        to: KL,
        fromAll: Set<Snoc<KI, KL>>,
        `as`: Data,
        location: FileLocation = FileLocation(1),
        block: CreateDSL<Snoc<KI, KL>>.() -> Unit = {}
    )
}

private class CreateDSLImpl<KII : HList, KIL : Any>(
    private val init: Snoc<KII, KIL>,
    private val parametersBuilder: MutableList<Intermediate.Applied<*, *>>
) :
    CreateDSL<Snoc<KII, KIL>> {

    context(type: DomainType<KL>)
    override fun <KL : Any> create(
        `as`: KL,
        from: Snoc<Snoc<KII, KIL>, KL>,
        location: FileLocation,
        block: CreateDSL<Snoc<Snoc<KII, KIL>, KL>>.() -> Unit
    ) {
        addSubCreate(
            Intermediate.Recreate<Snoc<KII, KIL>, KL, Nothing>(init * `as`, from, null),
            location
        )
        val createDSL = CreateDSLImpl<Snoc<KII, KIL>, KL>(init * `as`, parametersBuilder)
        block(createDSL)
    }

    context(type: KeyCombinableType<KL, Data>)
    override fun <KL : Any, Data : Any> create(
        `as`: KL,
        from: Snoc<Snoc<KII, KIL>, KL>,
        patchData: Data,
        block: CreateDSL<Snoc<Snoc<KII, KIL>, KL>>.() -> Unit,
        location: FileLocation
    ) {
        addSubCreate(Intermediate.Recreate(init * `as`, from, type to patchData), location)
        val createDSL = CreateDSLImpl<Snoc<KII, KIL>, KL>(init * `as`, parametersBuilder)
        block(createDSL)
    }

    context(type: KeyCombinableType<KL, Data>)
    override fun <KL : Any, Data : Any> merge(
        to: KL,
        fromAll: Set<Snoc<Snoc<KII, KIL>, KL>>,
        `as`: Data,
        location: FileLocation,
        block: CreateDSL<Snoc<Snoc<KII, KIL>, KL>>.() -> Unit
    ) {
        addSubCreate(Intermediate.CreateOrMerge(init * to, fromAll, type to `as`), location)
        val createDSL = CreateDSLImpl(init * to, parametersBuilder)
        block(createDSL)
    }

    context(type: DomainType<KLL>)
    private fun <KLL : Any> addSubCreate(
        parameter: Intermediate<Snoc<KII, KIL>, KLL>,
        location: FileLocation
    ) {
        parametersBuilder.add(
            Intermediate.Applied(
                parameter,
                type,
                hashSetOf(location)
            )
        )
    }
}

@Marker
sealed class ParameterInvalidatingDSL<KI : Snoc<*, *>>(
    init: KI,
    parametersBuilder: MutableList<Intermediate.Applied<*, *>>
) : ParameterDSL<KI>(init, parametersBuilder)

@Marker
class SplitDSL<KI : HList, KL : Any>(
    init: Snoc<KI, KL>,
    parametersBuilder: MutableList<Intermediate.Applied<*, *>>
) : ParameterInvalidatingDSL<Snoc<KI, KL>>(init, parametersBuilder) {

    context(type: KeyCombinableType<KL, Data>)
    fun <Data : Any> to(
        key: Snoc<KI, KL>,
        `as`: Data,
        location: FileLocation = FileLocation(1),
        block: CreateDSL<Snoc<KI, KL>>.() -> Unit = {}
    ) {
        parametersBuilder.add(
            Intermediate.Applied(
                Intermediate.CreateOrMerge(
                    key,
                    emptySet(),
                    type to `as`
                ), type, hashSetOf(location)
            )
        )
        val createDSLImpl = CreateDSLImpl(key, parametersBuilder)
        block(createDSLImpl)
    }
}

@Marker
sealed class ParameterNonInvalidatingDSL<KI : HList>(
    init: KI,
    parametersBuilder: MutableList<Intermediate.Applied<*, *>>
) :
    ParameterDSL<KI>(init, parametersBuilder), CreateDSL<KI> {

    context(type: DomainType<KL>)
    fun <KL : Any> adjust(
        from: KL,
        location: FileLocation = FileLocation(1),
        block: ParameterNonInvalidatingDSL<Snoc<KI, KL>>.() -> Unit
    ) {
        addParameter(Intermediate.Keep(init * from), location)
        val nonInvalidating =
            ParameterNestedNonInvalidatingDSL<Snoc<KI, KL>>(init * from, parametersBuilder)
        block(nonInvalidating)
    }

    context(type: DomainType<KL>)
    override fun <KL : Any> create(
        `as`: KL,
        from: Snoc<KI, KL>,
        location: FileLocation,
        block: CreateDSL<Snoc<KI, KL>>.() -> Unit
    ) {
        addParameter(Intermediate.Recreate<KI, KL, Nothing>(init * `as`, from, null), location)
        val createDSL = CreateDSLImpl<KI, KL>(init * `as`, parametersBuilder)
        block(createDSL)
    }

    context(type: KeyCombinableType<KL, Data>)
    override fun <KL : Any, Data : Any> create(
        `as`: KL,
        from: Snoc<KI, KL>,
        patchData: Data,
        block: CreateDSL<Snoc<KI, KL>>.() -> Unit,
        location: FileLocation
    ) {
        addParameter(Intermediate.Recreate(init * `as`, from, type to patchData), location)
        val createDSL = CreateDSLImpl<KI, KL>(init * `as`, parametersBuilder)
        block(createDSL)

    }

    context(type: KeyCombinableType<KL, Data>)
    override fun <KL : Any, Data : Any> merge(
        to: KL,
        fromAll: Set<Snoc<KI, KL>>,
        `as`: Data,
        location: FileLocation,
        block: CreateDSL<Snoc<KI, KL>>.() -> Unit
    ) {
        addParameter(Intermediate.CreateOrMerge(init * to, fromAll, Pair(type, `as`)), location)
        val createDSL = CreateDSLImpl<KI, KL>(init * to, parametersBuilder)
        block(createDSL)
    }



    context(type: DomainType<KL>)
    private fun <KL : Any> addParameter(
        parameter: Intermediate<KI, KL>,
        location: FileLocation
    ) {
        parametersBuilder.add(
            Intermediate.Applied(
                parameter, type, hashSetOf(location)
            )
        )
    }

    final override fun equals(other: Any?): Boolean {
        if (other === null) return false
        if (this === other) return true
        if (this::class !== other::class) return false
        return parametersBuilder == (other as ParameterNonInvalidatingDSL<*>).parametersBuilder
    }

    final override fun hashCode(): Int {
        return parametersBuilder.hashCode()
    }
}

@Marker
class ParameterNestedNonInvalidatingDSL<KI : Snoc<*, *>>(
    init: KI, parametersBuilder: MutableList<Intermediate.Applied<*, *>>
) : ParameterNonInvalidatingDSL<KI>(init, parametersBuilder)

@Marker
class ParameterRootDSL private constructor(
    parametersBuilder: MutableList<Intermediate.Applied<*, *>>
) : ParameterNonInvalidatingDSL<HList.Empty>(HList.Empty, parametersBuilder) {

    companion object {

        operator fun invoke(block: ParameterRootDSL.() -> Unit): EitherNel<Intermediate.Error, ParameterCollection> =
            either {
                val parametersBuilder = arrayListOf<Intermediate.Applied<*, *>>()
                val dsl = ParameterRootDSL(parametersBuilder)
                block(dsl)

                ParameterCollection(parametersBuilder).bind()
            }

    }
}

//fun <KL : Any> Applied<Parameter<HList.Empty, KL>, KL>.expandMapping(
//) = expandMapping(HList.Empty)
//
//fun <KI : HList, KL : Any> Applied<Parameter<out KI, out KL>, out KL>.expandMapping(
//    init: KI
//): Sequence<Pair<KeyMapping<*, *>, Applied<*, *>>> {
//
//    return when (val p = this.parameter) {
//        is Adjust<KI, KL> -> {
//            val element = KeyMapping(hashSetOf(init * p.keep), init * p.keep) to this
//            val plus = p.by.asSequence().flatMap { it.expandMapping(init * p.keep) }.toList()
//            val sum = plus + element
//            sum.asSequence()
//        }
//
//        is Split<*, *> -> {
//            p.by.asSequence().flatMap {
//                it.expandMapping(init * p.from)
//            }
//                .plus(KeyMapping(hashSetOf(init * p.from), null) to this)
//        }
//
//        is CreateOrMerge<*, *, *> -> p.also.asSequence()
//            .flatMap { it.expandMapping(init * p.to) }
//            .plus(KeyMapping(p.from, init * p.to) to this)
//
//        is Recreate<*, *, *> -> p.also.asSequence()
//            .flatMap { it.expandMapping(init * p.to) }
//            .plus(KeyMapping(hashSetOf(p.from), init * p.to) to this)
//
//        is Move<*, *> -> sequenceOf(KeyMapping(hashSetOf(init * p.from), p.to) to this)
//        is MoveRange<*, *> -> p.keySequence { fromLast, toInit, toLast ->
//            yield(
//                KeyMapping(
//                    hashSetOf(init * fromLast),
//                    toInit * toLast
//                ) to this@expandMapping
//            )
//        }
//    }
//
//}





