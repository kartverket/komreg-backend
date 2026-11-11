package no.kartverket.komreg.regulering.reform.format

import arrow.core.EitherNel
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.identTypeOf1
import no.kartverket.komreg.integration.spi.identTypeOf2
import no.kartverket.komreg.integration.spi.identTypeOf3
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.regulering.reform.domain.type.NumberingScheme
import no.kartverket.komreg.regulering.reform.rules.validateDomain
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.ReformOp
import kotlin.test.Test

class ResolveTest {
    private val registry = runBlocking { SegmentRegistry.v1(NumberingScheme.Production) }

    private fun resolve(vararg operasjoner: String): EitherNel<FormatErr, Resolved> =
        resolveJson("""{ "navn": "R", "ikrafttredelse": "2027-01-01", "operasjoner": [ ${operasjoner.joinToString()} ] }""")

    private fun resolveJson(json: String) = Json.decodeFromString(ReformSpec.serializer(), json).resolve(registry)

    private fun errors(vararg operasjoner: String): List<FormatErr> =
        resolve(*operasjoner).fold({ it }, { error("Forventet feil, fikk $it") })

    private fun ok(vararg operasjoner: String): Resolved = resolve(*operasjoner).fold({ error("Uventede feil: $it") }, { it })

    /** Koden og stien til hver feil. */
    private fun List<FormatErr>.codes() = map { it.code to it.path.value }

    private val fylke = runBlocking { identTypeOf1<Fylkesnummer>() }
    private val kommune = runBlocking { identTypeOf2<Fylkesnummer, Kommunenummer.Lopenummer>() }
    private val veg = runBlocking { identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode>() }

    private fun fylke(f: Long) = runBlocking { fylke(Fylkesnummer(f)) }
    private fun kommune(f: Long, k: Int) = runBlocking { kommune(Fylkesnummer(f), Kommunenummer.Lopenummer(k.toByte())) }
    private fun veg(f: Long, k: Int, a: Int) =
        runBlocking { veg(Fylkesnummer(f), Kommunenummer.Lopenummer(k.toByte()), Adressekode(a)) }

    @Test
    fun `the design example resolves and passes ReformClaims`() {
        val resolved = resolveJson(ReformSpecFixtures.valid("design-example")).fold({ error("$it") }, { it })

        assertThat(ReformClaims.validate(resolved.ops).isRight()).isTrue()
    }

    @Test
    fun `all valid fixtures resolve`() {
        ReformSpecFixtures.valid.forEach { name ->
            assertThat(resolveJson(ReformSpecFixtures.valid(name)).isRight(), name).isTrue()
        }
    }

    @Test
    fun `children are relative to the key of the node`() {
        val resolved = ok(
            """{ "op": "adjust", "kind": "county", "key": "46", "then": [
                 { "op": "move", "kind": "addressCode", "key": ["01", 1234], "to": ["46", "02", 1234] } ] }""",
        )

        assertThat(resolved.ops).containsExactly(ReformOp.Move(veg(46, 1, 1234), veg(46, 2, 1234)))
    }

    @Test
    fun `origins point to the node, source or part, and identical duplicates are merged`() {
        val resolved = ok(
            """{ "op": "merge", "kind": "county", "from": ["30", "31"], "into": { "new": ["33"], "payload": { "navn": "B" } } }""",
            """{ "op": "move", "kind": "addressCode", "key": ["46", "01", 1], "to": ["46", "02", 1] }""",
            """{ "op": "move", "kind": "addressCode", "key": ["46", "01", 1], "to": ["46", "02", 1] }""",
        )

        val move = ReformOp.Move(veg(46, 1, 1), veg(46, 2, 1))
        assertThat(resolved.ops).containsExactlyInAnyOrder(
            ReformOp.RetireTo(fylke(30), fylke(33)),
            ReformOp.RetireTo(fylke(31), fylke(33)),
            ReformOp.Create(fylke(33), Fylkesdata("B")),
            move,
        )
        assertThat(resolved.origins.mapValues { (_, paths) -> paths.map { it.value } }).isEqualTo(
            mapOf(
                ReformOp.RetireTo(fylke(30), fylke(33)) to listOf("$.operasjoner[0].from[0]"),
                ReformOp.RetireTo(fylke(31), fylke(33)) to listOf("$.operasjoner[0].from[1]"),
                ReformOp.Create(fylke(33), Fylkesdata("B")) to listOf("$.operasjoner[0].into"),
                move to listOf("$.operasjoner[1]", "$.operasjoner[2]"),
            ),
        )
    }

    @Test
    fun `split with an existing successor retires the source to it and derives the new parts`() {
        val resolved = ok(
            """{ "op": "split", "kind": "municipality", "key": ["50", "05"], "into": [
                 { "existing": ["50", "01"], "role": "successor" }, { "new": ["50", "10"] } ] }""",
        )

        assertThat(resolved.ops).containsExactly(
            ReformOp.Retire(kommune(50, 5), successor = kommune(50, 1)),
            ReformOp.Derive(kommune(50, 10), from = kommune(50, 5)),
        )
        assertThat(resolved.origins[ReformOp.Derive(kommune(50, 10), from = kommune(50, 5))]!!.map { it.value })
            .containsExactly("$.operasjoner[0].into[1]")
    }

    @Test
    fun `then under a part is KEEP for existing and TARGET for new`() {
        val resolved = ok(
            """{ "op": "split", "kind": "county", "key": "30", "into": [
                 { "existing": ["31"], "role": "successor", "then": [ { "op": "adjust", "kind": "municipality", "key": "01" } ] },
                 { "new": ["32"], "then": [ { "op": "create", "kind": "municipality", "key": "01", "payload": {
                     "navn": "K", "koordinatsystem": "UTM32", "senterpunkt": { "x": 1.0, "y": 2.0 }, "kommunevapen": "iVBORw0KGgo=" } } ] } ] }""",
        )
        assertThat(resolved.ops.filterIsInstance<ReformOp.Create>().map { it.target }).containsExactly(kommune(32, 1))

        val errors = errors(
            """{ "op": "split", "kind": "municipality", "key": ["50", "05"], "into": [
                 { "existing": ["50", "01"], "role": "successor" },
                 { "new": ["50", "10"], "then": [ { "op": "move", "kind": "addressCode", "key": 1, "to": ["50", "10", 1] },
                                                   { "op": "adjust", "kind": "addressCode", "key": 2 } ] } ] }""",
        )
        assertThat(errors.filter { it.code == "OpNotAllowedInContext" }.map { it.path.value }).containsExactly(
            "$.operasjoner[0].into[1].then[0]",
            "$.operasjoner[0].into[1].then[1]",
        )
    }

    @Test
    fun `KeptAndConsumed when an existing part is a source elsewhere`() {
        val errors = errors(
            """{ "op": "split", "kind": "municipality", "key": ["50", "05"], "into": [
                 { "existing": ["50", "01"], "role": "successor" }, { "new": ["50", "10"] } ] }""",
            """{ "op": "merge", "kind": "municipality", "from": [["50", "01"]], "into": { "existing": ["50", "02"] } }""",
        )

        assertThat(errors).containsExactly(
            FormatErr.KeptAndConsumed(JsonPath("$.operasjoner[0].into[0].existing"), "[county=50/municipality=01]", JsonPath("$.operasjoner[1].from[0]")),
        )
    }

    @Test
    fun `split with identity moves the source and derives the other parts`() {
        val resolved = ok(
            """{ "op": "split", "kind": "municipality", "key": ["46", "05"], "into": [
                 { "new": ["46", "10"], "role": "identity" }, { "new": ["46", "11"] } ] }""",
        )

        assertThat(resolved.ops).containsExactly(
            ReformOp.Move(kommune(46, 5), kommune(46, 10)),
            ReformOp.Derive(kommune(46, 11), from = kommune(46, 5)),
        )
    }

    @Test
    fun `an intermediate in a path pivot under a merge source gives no keeps`() {
        ok(
            """{ "op": "merge", "kind": "municipality",
                 "from": [ { "key": ["50", "01"], "then": [
                   { "op": "move", "kind": "titleNumber", "key": [12, 3], "to": ["50", "55", 40, 1] },
                   { "op": "split", "kind": "landNumber", "key": 12, "into": [] } ] } ],
                 "into": { "existing": ["50", "55"] } }""",
        )
    }

    @Test
    fun `the same path pivot on the root keeps the municipality`() {
        val errors = errors(
            """{ "op": "move", "kind": "titleNumber", "key": ["50", "01", 12, 3], "to": ["50", "55", 40, 1] }""",
            """{ "op": "merge", "kind": "municipality", "from": [["50", "01"]], "into": { "existing": ["50", "55"] } }""",
        )

        assertThat(errors).containsExactly(
            FormatErr.KeptAndConsumed(JsonPath("$.operasjoner[0].key[1]"), "[county=50/municipality=01]", JsonPath("$.operasjoner[1].from[0]")),
        )
    }

    @Test
    fun `KeptAndConsumed for adjust and merge into existing`() {
        val errors = errors(
            """{ "op": "adjust", "kind": "municipality", "key": ["46", "01"] }""",
            """{ "op": "merge", "kind": "municipality", "from": [["46", "02"]], "into": { "existing": ["46", "03"] } }""",
            """{ "op": "merge", "kind": "municipality", "from": [["46", "01"], ["46", "03"]], "into": { "existing": ["46", "04"] } }""",
        )

        assertThat(errors.codes()).containsExactlyInAnyOrder(
            "KeptAndConsumed" to "$.operasjoner[0].key[1]",
            "KeptAndConsumed" to "$.operasjoner[1].into.existing",
        )
        assertThat(errors.map { (it as FormatErr.KeptAndConsumed).sourcePath.value }).containsExactlyInAnyOrder(
            "$.operasjoner[2].from[0]",
            "$.operasjoner[2].from[1]",
        )
    }

    @Test
    fun UnknownKind() {
        assertThat(errors("""{ "op": "move", "kind": "road", "key": ["46", "01", 1], "to": ["46", "02", 1] }""").codes())
            .containsExactly("UnknownKind" to "$.operasjoner[0].kind")
    }

    @Test
    fun OpNotAllowedInContext() {
        val errors = errors(
            """{ "op": "move", "kind": "municipality", "key": ["46", "01"], "to": ["46", "02"], "then": [
                 { "op": "adjust", "kind": "addressCode", "key": 1 },
                 { "op": "create", "kind": "landNumber", "key": 1, "payload": {} } ] }""",
            """{ "op": "create", "kind": "county", "key": "60", "payload": { "navn": "N" }, "then": [
                 { "op": "move", "kind": "municipality", "key": "01", "to": ["60", "02"] } ] }""",
            """{ "op": "merge", "kind": "county", "from": ["30"], "into": { "new": ["33"], "then": [
                 { "op": "split", "kind": "municipality", "key": "01", "into": [] } ] } }""",
        )

        assertThat(errors.filter { it.code == "OpNotAllowedInContext" }.map { it.path.value }).containsExactly(
            "$.operasjoner[0].then[0]",
            "$.operasjoner[0].then[1]",
            "$.operasjoner[1].then[0]",
            "$.operasjoner[2].into.then[0]",
        )
    }

    @Test
    fun KindNotChildOfContext() {
        val errors = errors(
            """{ "op": "move", "kind": "addressCode", "key": 1234, "to": ["46", "02", 1234] }""",
            """{ "op": "adjust", "kind": "municipality", "key": ["46", "01"], "then": [
                 { "op": "move", "kind": "addressCode", "key": ["46", "01", 1], "to": ["46", "02", 1] },
                 { "op": "move", "kind": "county", "key": "47", "to": ["48"] } ] }""",
            """{ "op": "merge", "kind": "municipality", "from": [[]], "into": { "existing": ["46", "02"] } }""",
        )

        assertThat(errors).containsExactly(
            FormatErr.KindNotChildOfContext(JsonPath("$.operasjoner[0].key"), "addressCode", "roten", 1, 3),
            FormatErr.KindNotChildOfContext(
                JsonPath("$.operasjoner[1].then[0].key"),
                "addressCode",
                "[county=46/municipality=01]",
                3,
                1,
            ),
            FormatErr.KindNotChildOfContext(
                JsonPath("$.operasjoner[1].then[1].key"),
                "county",
                "[county=46/municipality=01]",
                1,
                null,
            ),
            FormatErr.KindNotChildOfContext(JsonPath("$.operasjoner[2].from[0]"), "municipality", "roten", 0, 2),
        )
    }

    @Test
    fun InvalidSegment() {
        ReformSpecFixtures.invalidValues.forEach { (name, expected) ->
            val errors = resolveJson(ReformSpecFixtures.invalidValues(name)).fold({ it }, { error("$name: $it") })

            assertThat(errors.single(), name).isInstanceOf<FormatErr.InvalidSegment>()
            assertThat(errors.single().path.value, name).isEqualTo(expected.path)
            assertThat((errors.single() as FormatErr.InvalidSegment).kind, name).isEqualTo(expected.kind)
        }
    }

    @Test
    fun TargetLengthMismatch() {
        assertThat(errors("""{ "op": "move", "kind": "addressCode", "key": ["46", "01", 1], "to": ["46", 1] }""").codes())
            .containsExactly("TargetLengthMismatch" to "$.operasjoner[0].to")
    }

    @Test
    fun MultipleRoles() {
        assertThat(
            errors(
                """{ "op": "split", "kind": "addressCode", "key": ["46", "01", 1], "into": [
                     { "new": ["46", "02", 1], "role": "identity" }, { "new": ["46", "03", 1], "role": "successor" } ] }""",
            ).codes(),
        ).containsExactly("MultipleRoles" to "$.operasjoner[0].into[1].role")
    }

    @Test
    fun `MultipleRoles counts an existing part and a new part with a role`() {
        assertThat(
            errors(
                """{ "op": "split", "kind": "municipality", "key": ["46", "05"], "into": [
                     { "existing": ["46", "01"], "role": "successor" }, { "new": ["46", "10"], "role": "identity" } ] }""",
            ).codes(),
        ).containsExactly("MultipleRoles" to "$.operasjoner[0].into[1].role")
    }

    @Test
    fun PayloadOnIdentity() {
        assertThat(
            errors(
                """{ "op": "split", "kind": "county", "key": "30", "into": [
                     { "new": ["31"], "role": "identity", "payload": { "navn": "X" } }, { "new": ["32"] } ] }""",
            ).codes(),
        ).containsExactly("PayloadOnIdentity" to "$.operasjoner[0].into[0].payload")
    }

    @Test
    fun EmptyMerge() {
        assertThat(errors("""{ "op": "merge", "kind": "county", "from": [], "into": { "existing": ["33"] } }""").codes())
            .containsExactly("EmptyMerge" to "$.operasjoner[0].from")
    }

    @Test
    fun PayloadRequired() {
        assertThat(errors("""{ "op": "merge", "kind": "county", "from": ["30", "31"], "into": { "new": ["33"] } }""").codes())
            .containsExactly("PayloadRequired" to "$.operasjoner[0].into")
    }

    @Test
    fun `PayloadRequired for create without payload and derivedFrom`() {
        assertThat(errors("""{ "op": "create", "kind": "municipality", "key": ["46", "10"] }""").codes())
            .containsExactly("PayloadRequired" to "$.operasjoner[0]")
    }

    @Test
    fun `create with derivedFrom resolves to Derive with and without payload`() {
        val resolved = ok(
            """{ "op": "create", "kind": "municipality", "key": ["46", "10"], "derivedFrom": ["46", "05"],
                 "payload": { "navn": "Ny", "koordinatsystem": "UTM32", "senterpunkt": { "x": 1.0, "y": 2.0 },
                 "godkjenteGardsnumre": [ { "fra": 1, "til": 3 } ], "kommunevapen": "iVBORw0KGgo=" } }""",
            """{ "op": "create", "kind": "municipality", "key": ["46", "11"], "derivedFrom": ["46", "05"] }""",
        )

        assertThat(resolved.ops.filterIsInstance<ReformOp.Derive>().map { it.target to it.from to (it.payload != null) })
            .containsExactly(
                kommune(46, 10) to kommune(46, 5) to true,
                kommune(46, 11) to kommune(46, 5) to false,
            )
        assertThat(resolved.ops).hasSize(2)
    }

    @Test
    fun `derivedFrom is checked as an absolute target`() {
        val errors = errors(
            """{ "op": "create", "kind": "municipality", "key": ["46", "10"], "derivedFrom": ["05"] }""",
            """{ "op": "create", "kind": "municipality", "key": ["46", "11"], "derivedFrom": ["46", "x"] }""",
        )

        assertThat(errors.codes()).containsExactly(
            "TargetLengthMismatch" to "$.operasjoner[0].derivedFrom",
            "InvalidSegment" to "$.operasjoner[1].derivedFrom[1]",
        )
    }

    @Test
    fun `derivedFrom from a source used by split gives no KeptAndConsumed`() {
        val resolved = ok(
            """{ "op": "split", "kind": "municipality", "key": ["46", "05"], "into": [ { "existing": ["50", "10"], "role": "successor" } ] }""",
            """{ "op": "create", "kind": "municipality", "key": ["46", "10"], "derivedFrom": ["46", "05"] }""",
        )

        assertThat(resolved.ops).containsExactly(
            ReformOp.Retire(kommune(46, 5), successor = kommune(50, 10)),
            ReformOp.Derive(kommune(46, 10), from = kommune(46, 5)),
        )
    }

    @Test
    fun `short form and create with derivedFrom give the same ops`() {
        val payload = """{ "navn": "Ny", "koordinatsystem": "UTM32", "senterpunkt": { "x": 1.0, "y": 2.0 },
            "godkjenteGardsnumre": [ { "fra": 1, "til": 3 } ], "kommunevapen": "iVBORw0KGgo=" }"""
        val short = ok(
            """{ "op": "split", "kind": "municipality", "key": ["46", "05"], "into": [
                 { "new": ["46", "06"], "role": "successor", "payload": $payload }, { "new": ["46", "10"], "payload": $payload } ] }""",
        )
        val canonical = ok(
            """{ "op": "split", "kind": "municipality", "key": ["46", "05"], "into": [
                 { "existing": ["46", "06"], "role": "successor" } ] }""",
            """{ "op": "create", "kind": "municipality", "key": ["46", "06"], "derivedFrom": ["46", "05"], "payload": $payload }""",
            """{ "op": "create", "kind": "municipality", "key": ["46", "10"], "derivedFrom": ["46", "05"], "payload": $payload }""",
        )

        assertThat(canonical.ops).containsExactlyInAnyOrder(*short.ops.toTypedArray())
    }

    @Test
    fun `create with derivedFrom under create then is relative to the TARGET`() {
        val resolved = ok(
            """{ "op": "create", "kind": "county", "key": "60", "payload": { "navn": "Nytt" }, "then": [
                 { "op": "create", "kind": "municipality", "key": "01", "derivedFrom": ["46", "05"] } ] }""",
        )

        assertThat(resolved.ops).containsExactly(
            ReformOp.Create(fylke(60), Fylkesdata("Nytt")),
            ReformOp.Derive(kommune(60, 1), from = kommune(46, 5)),
        )
        assertThat(ReformClaims.validate(resolved.ops).isRight()).isTrue()
    }

    @Test
    fun `shared successor fixture passes claims and domain rules`() {
        val resolved = resolveJson(ReformSpecFixtures.valid("split-shared-successor-derived")).fold({ error("$it") }, { it })

        val claims = ReformClaims.validate(resolved.ops).fold({ error("$it") }, { it })
        assertThat(claims.validateDomain(registry).isRight()).isTrue()
    }

    @Test
    fun `claims reject derivedFrom from a target and conflicting targets`() {
        val fromTarget = ok(
            """{ "op": "create", "kind": "municipality", "key": ["46", "10"], "derivedFrom": ["46", "11"] }""",
            """{ "op": "create", "kind": "municipality", "key": ["46", "11"], "derivedFrom": ["46", "05"] }""",
        )
        assertThat(ReformClaims.validate(fromTarget.ops).leftOrNull()!!.map { it::class.simpleName })
            .containsExactly("DeriveFromTarget")

        val conflict = ok(
            """{ "op": "split", "kind": "municipality", "key": ["46", "05"], "into": [ { "new": ["46", "10"] } ] }""",
            """{ "op": "create", "kind": "municipality", "key": ["46", "10"], "derivedFrom": ["46", "06"] }""",
        )
        assertThat(ReformClaims.validate(conflict.ops).leftOrNull()!!.map { it::class.simpleName })
            .containsExactly("Conflict")
    }

    @Test
    fun PayloadNotAllowed() {
        assertThat(
            errors("""{ "op": "create", "kind": "addressCode", "key": ["46", "01", 1], "payload": { "navn": "X" } }""").codes(),
        ).containsExactly("PayloadNotAllowed" to "$.operasjoner[0].payload")
    }

    @Test
    fun InvalidPayload() {
        val errors = errors(
            """{ "op": "merge", "kind": "county", "from": ["30", "31"], "into": { "new": ["33"], "payload": { "name": "B" } } }""",
            """{ "op": "create", "kind": "municipality", "key": ["46", "99"],
                 "payload": { "navn": "K", "koordinatsystem": "UTM32", "senterpunkt": { "x": 1.0, "y": 2.0 }, "kommunevapen": "!" } }""",
        )

        assertThat(errors.codes()).containsExactly(
            "InvalidPayload" to "$.operasjoner[0].into.payload",
            "InvalidPayload" to "$.operasjoner[1].payload",
        )
    }

    @Test
    fun `all errors are collected in one response`() {
        val errors = errors(
            """{ "op": "move", "kind": "road", "key": 1, "to": [1] }""",
            """{ "op": "merge", "kind": "county", "from": [], "into": { "new": ["3"], "payload": { "navn": 1 } } }""",
            """{ "op": "split", "kind": "addressCode", "key": ["46", "01", 0], "into": [
                 { "new": ["46", "02"] }, { "new": ["46", "03", 1], "role": "identity", "payload": {} } ],
                 "then": [ { "op": "create", "kind": "addressNumber", "key": 1, "payload": {} } ] }""",
        )

        assertThat(errors.codes()).containsExactlyInAnyOrder(
            "UnknownKind" to "$.operasjoner[0].kind",
            "EmptyMerge" to "$.operasjoner[1].from",
            "InvalidPayload" to "$.operasjoner[1].into.payload",
            "InvalidSegment" to "$.operasjoner[1].into.new[0]",
            "PayloadOnIdentity" to "$.operasjoner[2].into[1].payload",
            "InvalidSegment" to "$.operasjoner[2].key[2]",
            "TargetLengthMismatch" to "$.operasjoner[2].into[0].new",
        )
    }
}
