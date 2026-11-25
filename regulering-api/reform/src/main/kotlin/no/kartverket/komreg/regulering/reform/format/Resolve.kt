package no.kartverket.komreg.regulering.reform.format

import arrow.core.EitherNel
import arrow.core.left
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Payload
import no.kartverket.komreg.transformation.reform.ReformOp

/**
 * Resultatet av [resolve]: op-ene i specen, uten identiske duplikater, i rekkefølgen de først forekommer.
 * [origins] gir JSON-stiene hver op kommer fra (flere hvis den forekommer flere ganger).
 */
data class Resolved(
    val ops: List<ReformOp>,
    val origins: Map<ReformOp, List<JsonPath>>,
)

/**
 * Tolker specen med [registry] og lager `ReformOp` direkte, i én gjennomgang av treet. Samler alle feil.
 * Kaller ikke `ReformClaims.validate`. Se `docs/reform-format.md`.
 */
fun ReformSpec.resolve(registry: SegmentRegistry): EitherNel<FormatErr, Resolved> {
    val resolver = Resolver(registry, ikrafttredelse)
    resolver.walkAll(operasjoner, Key.Empty, Mode.KEEP, JsonPath.Root / "operasjoner")
    resolver.checkKeptAndConsumed()

    resolver.errors.toNonEmptyListOrNull()?.let { return it.left() }
    val origins = LinkedHashMap<ReformOp, MutableList<JsonPath>>()
    resolver.ops.forEach { (op, path) -> origins.getOrPut(op) { mutableListOf() } += path }
    return Resolved(origins.keys.toList(), origins).right()
}

/** En dekodet pivot: nøkkelen og mellomleddene foran den, med JSON-stiene til segmentene. */
private class Pivot(val key: Key.Snoc, val intermediates: List<Pair<Key.Snoc, JsonPath>>, val keyPath: JsonPath)

private class Resolver(private val registry: SegmentRegistry, private val ikrafttredelse: LocalDate) {
    val errors = mutableListOf<FormatErr>()
    val ops = mutableListOf<Pair<ReformOp, JsonPath>>()

    /** Nøkler som står i ro: `adjust.key` og mellomledd i KEEP-kontekst. */
    private val keeps = mutableListOf<Pair<Key.Snoc, JsonPath>>()

    fun walkAll(nodes: List<OpSpec>, context: Key, mode: Mode, path: JsonPath) =
        nodes.forEachIndexed { i, node -> walk(node, context, mode, path / i) }

    private fun walk(node: OpSpec, context: Key, mode: Mode, path: JsonPath) {
        if (node.opName !in mode.allowedOps) errors += FormatErr.OpNotAllowedInContext(path, node.opName, mode)
        checkShape(node, path)

        val kind = registry[node.kind]
        if (kind == null) {
            errors += FormatErr.UnknownKind(path / "kind", node.kind)
            return
        }

        when (node) {
            is OpSpec.Adjust -> {
                val pivot = pivot(context, kind, node.key, path / "key") ?: return
                if (mode == Mode.KEEP) {
                    keeps += pivot.intermediates
                    keeps += pivot.key to pivot.keyPath
                }
                walkAll(node.then, pivot.key, Mode.KEEP, path / "then")
            }

            is OpSpec.Move -> {
                val pivot = pivot(context, kind, node.key, path / "key")
                val target = target(kind, node.to, path / "to")
                pivot ?: return
                keepIntermediates(pivot, mode)
                target?.let { paired(pivot.key, it) { s, t -> emit(ReformOp.Move(s, t), path) } }
                walkAll(node.then, pivot.key, Mode.SOURCE, path / "then")
            }

            is OpSpec.Merge -> merge(node, kind, context, mode, path)

            is OpSpec.Split -> split(node, kind, context, mode, path)

            is OpSpec.Create -> {
                val pivot = pivot(context, kind, node.key, path / "key")
                val payload = node.payload?.let { payload(kind, it, path / "payload") }
                val derivedFrom = node.derivedFrom?.let { target(kind, it, path / "derivedFrom") }
                pivot ?: return
                keepIntermediates(pivot, mode)
                when {
                    node.derivedFrom != null ->
                        derivedFrom?.let { source ->
                            if (node.payload == null || payload != null) {
                                paired(source, pivot.key) { s, t -> emit(ReformOp.Derive(t, from = s, payload), path) }
                            }
                        }

                    payload != null -> pivot.key.toIdents(registry).forEach { emit(ReformOp.Create(it, payload), path) }
                }
                walkAll(node.then, pivot.key, Mode.TARGET, path / "then")
            }
        }
    }

    /** Regler som ikke avhenger av kinden, slik at de rapporteres også når kinden er ukjent. */
    private fun checkShape(node: OpSpec, path: JsonPath) {
        when (node) {
            is OpSpec.Merge -> if (node.from.isEmpty()) errors += FormatErr.EmptyMerge(path / "from")

            is OpSpec.Create ->
                if (node.payload == null && node.derivedFrom == null) errors += FormatErr.PayloadRequired(path)

            is OpSpec.Adjust, is OpSpec.Move, is OpSpec.Split -> Unit
        }
    }

    private fun merge(node: OpSpec.Merge, kind: SegmentKind<*>, context: Key, mode: Mode, path: JsonPath) {
        val target = target(kind, node.into, path / "into")
        node.from.forEachIndexed { i, source ->
            val sourcePath = path / "from" / i
            val keyPath = if (source is SourceSpec.Full) sourcePath / "key" else sourcePath
            val pivot = pivot(context, kind, source.key, keyPath) ?: return@forEachIndexed
            keepIntermediates(pivot, mode)
            target?.let { paired(pivot.key, it) { s, t -> emit(ReformOp.RetireTo(s, t), sourcePath) } }
            walkAll(source.then, pivot.key, Mode.SOURCE, sourcePath / "then")
        }
    }

    private fun split(node: OpSpec.Split, kind: SegmentKind<*>, context: Key, mode: Mode, path: JsonPath) {
        val pivot = pivot(context, kind, node.key, path / "key")
        val successor = node.successor?.let { target(kind, it, path / "successor") }
        pivot ?: return
        keepIntermediates(pivot, mode)
        when {
            node.successor == null -> pivot.key.toIdents(registry).forEach { emit(ReformOp.Retire(it), path) }
            successor != null -> paired(pivot.key, successor) { s, t -> emit(ReformOp.Retire(s, successor = t), path) }
        }
        walkAll(node.then, pivot.key, Mode.SOURCE, path / "then")
    }

    /** Mellomledd i en sti-pivot står i ro i KEEP-kontekst, og er bare navigasjon ellers. */
    private fun keepIntermediates(pivot: Pivot, mode: Mode) {
        if (mode == Mode.KEEP) keeps += pivot.intermediates
    }

    /**
     * Dekoder en relativ pivot. Kindene til leddene utledes baklengs fra [kind], og første ledd må være barn av
     * [context]. Gir `null` hvis nøkkelen ikke kan beregnes.
     */
    private fun pivot(context: Key, kind: SegmentKind<*>, spec: PathSpec, path: JsonPath): Pivot? {
        val segments = spec.segments
        val start = kind.path.size - segments.size
        if (segments.isEmpty() || start < 0 || kind.path[start].parent != context.kind) {
            val contextIndex = context.kind?.let { kind.path.indexOf(it) } ?: -1
            val expected = when {
                context == Key.Empty -> kind.path.size
                contextIndex >= 0 && contextIndex < kind.path.lastIndex -> kind.path.size - contextIndex - 1
                else -> null
            }
            errors += FormatErr.KindNotChildOfContext(path, kind.name, context.describe(), segments.size, expected)
            return null
        }

        fun segmentPath(j: Int) = if (spec is PathSpec.Path) path / j else path
        val keys = decode(context, kind.path.drop(start), segments, ::segmentPath) ?: return null
        return Pivot(
            key = keys.last(),
            intermediates = keys.dropLast(1).mapIndexed { j, key -> key to segmentPath(j) },
            keyPath = segmentPath(segments.lastIndex),
        )
    }

    /** Dekoder et absolutt mål med kind-stien til [kind]. */
    private fun target(kind: SegmentKind<*>, spec: KeySpec, path: JsonPath): Key.Snoc? {
        if (spec.segments.size != kind.path.size) {
            errors += FormatErr.TargetLengthMismatch(path, kind.name, kind.path.size, spec.segments.size)
            return null
        }
        return decode(Key.Empty, kind.path, spec.segments) { path / it }?.last()
    }

    /** Dekoder segmentene som [kinds] under [context]. Gir nøkkelen etter hvert segment, eller `null` ved feil. */
    private fun decode(
        context: Key,
        kinds: List<SegmentKind<*>>,
        segments: List<JsonPrimitive>,
        segmentPath: (Int) -> JsonPath,
    ): List<Key.Snoc>? {
        val values = segments.mapIndexed { j, segment ->
            kinds[j].decode(segment)
                .onLeft { errors += FormatErr.InvalidSegment(segmentPath(j), it.kind, it.value, it.reason) }
                .getOrNull()
        }
        if (values.any { it == null }) return null
        var key = context
        return values.mapIndexed { j, value -> key.snoc(kinds[j], value!!).also { key = it } }
    }

    private fun payload(kind: SegmentKind<*>, json: JsonObject, path: JsonPath): Payload? =
        kind.decodePayload(json, ikrafttredelse).fold(
            { err ->
                errors += when (err) {
                    is SegmentErr.PayloadNotAllowed -> FormatErr.PayloadNotAllowed(path, kind.name)
                    is SegmentErr.InvalidPayload -> FormatErr.InvalidPayload(path, kind.name, err.reason)
                    is SegmentErr.InvalidValue -> FormatErr.InvalidPayload(path, kind.name, err.reason)
                }
                null
            },
            { it },
        )

    /** Parer ekspansjonene av [source] og [target] etter indeks. De har samme kind, og dermed like mange. */
    private fun paired(source: Key.Snoc, target: Key.Snoc, op: (Ident.NonEmpty, Ident.NonEmpty) -> Unit) =
        source.toIdents(registry).zip(target.toIdents(registry)).forEach { (s, t) -> op(s, t) }

    private fun emit(op: ReformOp, path: JsonPath) {
        ops += op to path
    }

    /** Nøkler som står i ro, kan ikke samtidig være kilde i en annen node. */
    fun checkKeptAndConsumed() {
        val sources = ops.mapNotNull { (op, path) -> (op as? ReformOp.SourceOp)?.let { it.source to path } }
            .groupBy({ it.first }, { it.second })
        keeps.forEach { (key, keepPath) ->
            key.toIdents(registry)
                .flatMap { sources[it].orEmpty() }
                .distinct()
                .forEach { errors += FormatErr.KeptAndConsumed(keepPath, key.toString(), it) }
        }
    }
}

private fun Key.describe(): String = when (this) {
    Key.Empty -> "roten"
    is Key.Snoc -> toString()
}
