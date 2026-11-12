package no.kartverket.komreg.regulering.api.server.route.regulering

import io.ktor.openapi.AdditionalProperties
import io.ktor.openapi.GenericElement
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonSchemaDiscriminator
import io.ktor.openapi.JsonType
import io.ktor.openapi.ReferenceOr
import kotlinx.serialization.json.Json

/**
 * Håndskrevne JSON-skjemaer for reform-formatet (`docs/reform-format.md`).
 *
 * ReformSpec bruker egne serializers for felter med flere former (nøkler, kilder, `into` og delene i `split`), så
 * skjemaene kan ikke utledes fra `@Serializable`-typene. Skjemaene følger det parseren
 * (`regulering-api/reform/.../format/ReformSpec.kt` og `SpecSerializers.kt`) godtar, og i tillegg konteksten
 * hver operasjon står i (se [ReformSpecSchemas.sourceOp]).
 *
 * Skjemaer med `title` blir komponenter i OpenAPI-dokumentet. Skjemaer som refererer til hverandre, enten
 * fordi de er rekursive eller delt, gjør det med [ref].
 */
internal object ReformSpecSchemas {
    private val STRING = JsonType.STRING
    private val INTEGER = JsonType.INTEGER

    fun ref(name: String): ReferenceOr<JsonSchema> = ReferenceOr.schema(name)

    internal fun inline(schema: JsonSchema): ReferenceOr<JsonSchema> = ReferenceOr.value(schema)

    /** En referanse med egen beskrivelse på stedet den brukes. */
    internal fun described(description: String, target: ReferenceOr<JsonSchema>): ReferenceOr<JsonSchema> =
        inline(JsonSchema(description = description, allOf = listOf(target)))

    /** Et objektskjema som også kan være `null`. */
    internal fun nullableObject(description: String, schema: JsonSchema): ReferenceOr<JsonSchema> =
        inline(JsonSchema(description = description, anyOf = listOf(inline(schema), inline(JsonSchema(type = JsonType.NULL)))))

    internal fun nullable(type: JsonType): JsonSchema.SchemaType =
        JsonSchema.SchemaType.AnyOf(listOf(type, JsonType.NULL))

    internal fun string(description: String? = null, format: String? = null, nullable: Boolean = false) =
        inline(
            JsonSchema(
                type = if (nullable) nullable(STRING) else STRING,
                description = description,
                format = format,
            ),
        )

    internal fun integer(description: String? = null, minimum: Double? = null, maximum: Double? = null) =
        inline(JsonSchema(type = INTEGER, description = description, minimum = minimum, maximum = maximum))

    internal fun list(items: ReferenceOr<JsonSchema>, description: String, minItems: Int? = null) =
        inline(JsonSchema(type = JsonType.ARRAY, description = description, items = items, minItems = minItems))

    internal fun const(value: String, description: String? = null) =
        inline(JsonSchema(type = STRING, description = description, enum = listOf(GenericElement(value))))

    internal fun example(json: String): GenericElement = GenericElement(Json.parseToJsonElement(json))

    internal fun obj(
        title: String,
        description: String,
        properties: Map<String, ReferenceOr<JsonSchema>>,
        required: List<String>,
        example: String? = null,
    ) = JsonSchema(
        type = JsonType.OBJECT,
        title = title,
        description = description,
        properties = properties,
        required = required,
        additionalProperties = AdditionalProperties.Allowed(false),
        example = example?.let(::example),
    )

    // Kinds og segmenter

    val kinds = listOf(
        "county", "municipality", "addressCode", "addressNumber", "districtType",
        "districtNumber", "landNumber", "titleNumber", "parcel",
    )

    val kind = JsonSchema(
        type = STRING,
        title = "Kind",
        description = """
            Typen til et segment. Hver kind har én fast forelder, så kinden avgjør hele stien fra roten. En
            `addressNumber` er alltid `county → municipality → addressCode → addressNumber`.

            | kind | forelder | verdi i JSON | eksempel | tar payload |
            |---|---|---|---|---|
            | `county` (fylke) | ingen | streng, 2 siffer (5 siffer godtas i test) | `"46"` | ja |
            | `municipality` (kommune) | `county` | streng, 2 siffer (løpenummeret) | `"01"` | ja |
            | `addressCode` (veg) | `municipality` | tall, 1–99998 | `1234` | nei |
            | `addressNumber` (vegadresse) | `addressCode` | tall, 1–9998 | `5` | nei |
            | `districtType` (kretstype) | `municipality` | streng, ikke tom | `"valg"` | nei |
            | `districtNumber` (krets) | `districtType` | tall | `3` | nei |
            | `landNumber` (gårdsnummer) | `municipality` | tall, 0–99999 | `40` | nei |
            | `titleNumber` (bruksnummer) | `landNumber` | tall, 0–9999 | `1` | nei |
            | `parcel` (teig) | `municipality` | tall (teig-ID) | `987654` | nei |

            Kommunen er bare løpenummeret (`"01"`). Kommunenummeret 4601 er fylke og løpenummer sammen. En teig uten
            matrikkelnummer ligger i matrikkelen under to plassholdere («mnr mangler» og «mnr vann mangler»), og én
            operasjon på en `parcel` gjelder begge.
        """.trimIndent(),
        enum = kinds.map { GenericElement(it) },
    )

    val segment = JsonSchema(
        type = JsonSchema.SchemaType.AnyOf(listOf(STRING, INTEGER)),
        title = "Segment",
        description = """
            Ett ledd i en nøkkel. Verdien må ha typen kinden på plassen krever (se `Kind`): strenger og tall skilles.
            `"46"` er et gyldig fylke, men `46` er det ikke, og `"1"` er ikke et gyldig bruksnummer. Fylke og kommune
            er strenger for å bevare ledende nuller (`"03"`, `"01"`). Ugyldige verdier gir `InvalidSegment` i steget
            `format`.
        """.trimIndent(),
        examples = listOf(example("\"46\""), example("1234")),
    )

    val pathSpec = JsonSchema(
        title = "PathSpec",
        description = """
            Pivot: nøkkelen en node gjelder (`key` og elementene i `merge.from`). Skrives relativt til konteksten, som er
            nøkkelen til noden over. På rotnivå er konteksten tom.

            - En skalar er ett segment av typen `kind`: `"key": 1234`.
            - En array er en sti av segmenter som slutter på `kind`: `"key": [12, 3]`. Første segment må være et barn av
              konteksten. På rotnivå starter stien derfor med fylket: `"key": ["46", "01", 1234]`.

            **Mellomledd:** I en sti som `["50", "01", 12, 3]` er `50`, `5001` og `5001-12` mellomledd. Der nøkkelen står
            i ro (for eksempel på rotnivå), betyr mellomleddene det samme som et `adjust`: de står også i ro. Under en
            kilde eller en ny nøkkel er de bare en vei frem til nøkkelen. En nøkkel som står i ro som mellomledd og er
            kilde i en annen node, gir `KeptAndConsumed`.
        """.trimIndent(),
        oneOf = listOf(
            inline(JsonSchema(type = JsonSchema.SchemaType.AnyOf(listOf(STRING, INTEGER)), description = "Ett segment av typen `kind`.")),
            list(inline(segment), "Sti av segmenter fra konteksten og ned til `kind`.", minItems = 1),
        ),
        examples = listOf(example("1234"), example("[12, 3]"), example("[\"46\", \"01\", 1234]")),
    )

    val keySpec = JsonSchema(
        type = JsonType.ARRAY,
        title = "KeySpec",
        description = """
            Mål: en nøkkel noe flyttes til, slås sammen inn i, deles til eller avledes fra (`to`, `derivedFrom`,
            `into.existing`, `into.new`, og `existing` og `new` i delene av `split.into`). Skrives alltid som en full
            array av segmenter fra roten, uansett hvor i treet noden står, fordi et mål kan ligge hvor som helst, også i
            et annet fylke. Et mål har samme kind som pivoten i noden. Er målet ikke en full nøkkel fra roten til `kind`,
            gir det `TargetLengthMismatch`.
        """.trimIndent(),
        items = inline(segment),
        minItems = 1,
        examples = listOf(example("[\"46\", \"02\", 1234]"), example("[\"33\"]")),
    )

    // Payload

    private val koordinatsystem = JsonSchema(
        type = STRING,
        title = "Koordinatsystem",
        description = "Koordinatsystemet et senterpunkt er oppgitt i.",
        enum = listOf("UTM32", "UTM33", "UTM35", "UKJENT").map { GenericElement(it) },
    )

    private val koordinat = obj(
        "Koordinat",
        "Et punkt i et koordinatsystem. `x` er østing og `y` er nording.",
        mapOf("x" to inline(JsonSchema(type = JsonType.NUMBER, format = "double")), "y" to inline(JsonSchema(type = JsonType.NUMBER, format = "double"))),
        listOf("x", "y"),
        """{ "x": 230000.0, "y": 6630000.0 }""",
    )

    private val gardsnummerserie = obj(
        "Gardsnummerserie",
        "En serie med gårdsnummer fra og med `fra` til og med `til`. Krever `1 ≤ fra ≤ til`.",
        mapOf("fra" to integer(minimum = 1.0), "til" to integer(minimum = 1.0)),
        listOf("fra", "til"),
        """{ "fra": 1, "til": 200 }""",
    )

    val fylkePayload = obj(
        "FylkePayload",
        "Payload for `county`. Fylkesnummeret kommer fra nøkkelen.",
        mapOf("navn" to string("Navnet på fylket. Fylker beholder skrivemåten.")),
        listOf("navn"),
        """{ "navn": "Buskerud" }""",
    )

    private val kommuneAdresse = obj(
        "KommuneAdresse",
        "Postadressen til kommunen. Tomme adresselinjer fjernes.",
        mapOf(
            "adresselinje1" to string(nullable = true),
            "adresselinje2" to string(nullable = true),
            "postnummer" to string("Påkrevd i adressen."),
        ),
        listOf("postnummer"),
        """{ "adresselinje1": "Postboks 1", "adresselinje2": null, "postnummer": "3001" }""",
    )

    val kommunePayload = obj(
        "KommunePayload",
        "Payload for `municipality`. Fylkes- og kommunenummer kommer fra nøkkelen, og ikrafttredelsesdatoen fra " +
            "`ikrafttredelse` på toppnivå. Ukjente felter er en feil (`InvalidPayload`).",
        mapOf(
            "navn" to string("Navnet på kommunen. Lagres med store bokstaver."),
            "koordinatsystem" to described("Koordinatsystemet til `senterpunkt`.", inline(koordinatsystem)),
            "senterpunkt" to described("Senterpunktet til kommunen.", inline(koordinat)),
            "nedsattKonsesjonsgrense" to inline(
                JsonSchema(type = JsonType.BOOLEAN, default = GenericElement(false), description = "Standard `false`."),
            ),
            "godkjenteGardsnumre" to list(
                inline(gardsnummerserie),
                "Serier med godkjente gårdsnumre. Standard er en tom liste.",
            ),
            "adresse" to nullableObject("Postadressen til kommunen. Valgfri.", kommuneAdresse),
            "standardRekvirentOrgnummer" to string("Organisasjonsnummeret til standard rekvirent.", nullable = true),
            "kommunevapen" to string(
                "Kommunevåpenet som base64. Valgfri i formatet, men en ny kommune uten kommunevåpen avvises av " +
                    "domenereglene (`MissingMunicipalityCoat`). Ugyldig base64 gir `InvalidPayload`.",
                format = "byte",
                nullable = true,
            ),
        ),
        listOf("navn", "koordinatsystem", "senterpunkt"),
        """
            { "navn": "Nye Kommune", "koordinatsystem": "UTM32", "senterpunkt": { "x": 230000.0, "y": 6630000.0 },
              "nedsattKonsesjonsgrense": false, "godkjenteGardsnumre": [ { "fra": 1, "til": 200 } ],
              "adresse": { "adresselinje1": "Postboks 1", "adresselinje2": null, "postnummer": "3001" },
              "standardRekvirentOrgnummer": "123456789", "kommunevapen": "iVBORw0KGgo=" }
        """.trimIndent(),
    )

    val payload = JsonSchema(
        title = "Payload",
        description = """
            Data til en ny entitet. Formen avhenger av `kind`: `FylkePayload` for `county` og `KommunePayload` for
            `municipality`. Andre kinds tar ikke payload (`PayloadNotAllowed`). Payload som ikke kan leses (manglende
            eller ukjente felter, feil type) gir `InvalidPayload`.
        """.trimIndent(),
        oneOf = listOf(inline(fylkePayload), inline(kommunePayload)),
    )

    // Operasjoner. Rekursive og delte skjemaer refereres med navn, resten ligger inline der de brukes første gang.

    private val kindProperty = described(
        "Typen til nøkkelen noden gjelder. Se `Kind` for forelder og gyldige verdier.",
        inline(kind),
    )

    private fun op(name: String) = const(name, "Operasjonen. Avgjør hvilke andre felter noden har.")

    private val pathKey = described("Pivot: nøkkelen noden gjelder, relativt til konteksten.", inline(pathSpec))

    private fun target(description: String) = described(description, inline(keySpec))

    private val optionalPayload = described(
        "Data til den nye entiteten. Bare for `county` og `municipality`.",
        inline(payload),
    )

    private fun thenOf(items: ReferenceOr<JsonSchema>, description: String) = list(items, description)

    private const val ANY_CONTEXT =
        "Barn av noden. Pivotene deres er relative til nøkkelen til noden. Nøkkelen står i ro, så alle op-er er lov."
    private const val SOURCE_CONTEXT =
        "Barn av noden. Pivotene deres er relative til kilden. Under en kilde følger alt uten egen regel med kilden, " +
            "så bare `move`, `split` og `merge` er lov (`OpNotAllowedInContext`)."
    private const val NEW_CONTEXT =
        "Barn av noden. Pivotene deres er relative til den nye nøkkelen. Under en ny nøkkel finnes ingenting fra før, " +
            "så bare `create` er lov (`OpNotAllowedInContext`)."

    private fun opDiscriminator(vararg ops: String) = JsonSchemaDiscriminator(
        propertyName = "op",
        mapping = ops.associateWith { "#/components/schemas/${it.replaceFirstChar(Char::uppercase)}Op" },
    )

    val sourceOp = JsonSchema(
        title = "SourceOpSpec",
        description = "Operasjonene som er lov under en kilde (`move.then`, `split.then` og `merge.from[].then`): " +
            "`move`, `split` og `merge`. Under en kilde følger alt uten egen regel med kilden.",
        oneOf = listOf(ref("MoveOp"), ref("SplitOp"), ref("MergeOp")),
        discriminator = opDiscriminator("move", "split", "merge"),
    )

    private fun sourceOpItems(): ReferenceOr<JsonSchema> = ref("SourceOpSpec")

    val adjustOp = obj(
        "AdjustOp",
        "Ingen endring. Gir kontekst til barna, for å nå nøkler under noe som står i ro.",
        mapOf(
            "op" to op("adjust"),
            "kind" to kindProperty,
            "key" to pathKey,
            "then" to thenOf(ref("OpSpec"), ANY_CONTEXT),
        ),
        listOf("op", "kind", "key"),
        """
            { "op": "adjust", "kind": "municipality", "key": ["46", "01"], "then": [
              { "op": "move", "kind": "addressCode", "key": 1234, "to": ["46", "02", 1234] },
              { "op": "move", "kind": "titleNumber", "key": [12, 3], "to": ["46", "02", 40, 1] } ] }
        """.trimIndent(),
    )

    val moveOp = obj(
        "MoveOp",
        "Entiteten får ny nøkkel og beholder ID-en. Alt under den som ikke har en egen regel, følger med. Fylker og " +
            "kommuner kan ikke flyttes. Bruk `merge` med `into.new` for å gi en kommune nytt nummer.",
        mapOf(
            "op" to op("move"),
            "kind" to kindProperty,
            "key" to pathKey,
            "to" to target("Den nye nøkkelen. Full nøkkel fra roten, av samme kind som `key`."),
            "then" to thenOf(inline(sourceOp), SOURCE_CONTEXT),
        ),
        listOf("op", "kind", "key", "to"),
        """
            { "op": "move", "kind": "addressCode", "key": ["46", "01", 1234], "to": ["46", "02", 1234], "then": [
              { "op": "move", "kind": "addressNumber", "key": 5, "to": ["46", "02", 1300, 5] } ] }
        """.trimIndent(),
    )

    private val mergeSource = obj(
        "MergeSource",
        "En kilde med unntak: `then` er relativ til kilden. En kilde uten unntak skrives som en bar pivot.",
        mapOf(
            "key" to pathKey,
            "then" to thenOf(sourceOpItems(), SOURCE_CONTEXT),
        ),
        listOf("key"),
        """
            { "key": ["50", "01"], "then": [
              { "op": "move", "kind": "titleNumber", "key": [12, 3], "to": ["50", "55", 40, 1] } ] }
        """.trimIndent(),
    )

    private val sourceSpec = JsonSchema(
        title = "SourceSpec",
        description = "Et element i `merge.from`: en pivot, eller `{\"key\": pivot, \"then\": [...]}` når kilden har unntak.",
        oneOf = listOf(inline(pathSpec), inline(mergeSource)),
    )

    private val mergeIntoExisting = obj(
        "MergeIntoExisting",
        "Slår kildene inn i et mål som ikke opprettes av denne noden. Målet kan finnes fra før, eller opprettes et " +
            "annet sted i reformen (`create` med `derivedFrom`).",
        mapOf(
            "existing" to target("Målet. Full nøkkel fra roten, av samme kind som kildene."),
            "then" to thenOf(ref("OpSpec"), "Barn av målet, relative til målet. Målet står i ro, så alle op-er er lov."),
        ),
        listOf("existing"),
        """{ "existing": ["46", "02"] }""",
    )

    private val mergeIntoNew = obj(
        "MergeIntoNew",
        "Noden oppretter målet. Med én kilde er det en renummerering: målet er en ny entitet med ny ID, avledet fra " +
            "kilden. Med to eller flere kilder er det et nytt mål uten én bestemt kilde, og `payload` er da påkrevd " +
            "(`PayloadRequired`).",
        mapOf(
            "new" to target("Det nye målet. Full nøkkel fra roten, av samme kind som kildene."),
            "payload" to optionalPayload,
            "then" to thenOf(ref("CreateOp"), NEW_CONTEXT),
        ),
        listOf("new"),
        """
            { "new": ["50", "01"], "payload": { "navn": "Nytt Navn", "koordinatsystem": "UTM32",
              "senterpunkt": { "x": 230000.0, "y": 6630000.0 }, "kommunevapen": "iVBORw0KGgo=" } }
        """.trimIndent(),
    )

    private val mergeInto = JsonSchema(
        title = "MergeInto",
        description = "Målet i `merge`: enten `existing` eller `new`, aldri begge.",
        oneOf = listOf(inline(mergeIntoExisting), inline(mergeIntoNew)),
    )

    val mergeOp = obj(
        "MergeOp",
        "Kildene i `from` utgår og peker på målet i `into`. Det som ligger under kildene, og som ikke har egen regel, " +
            "følger med til målet.",
        mapOf(
            "op" to op("merge"),
            "kind" to kindProperty,
            "from" to list(
                inline(sourceSpec),
                "Kildene. Hver er en pivot relativt til konteksten, eller en kilde med unntak. Minst én (`EmptyMerge`).",
                minItems = 1,
            ),
            "into" to described("Målet kildene slås inn i.", inline(mergeInto)),
        ),
        listOf("op", "kind", "from", "into"),
        """
            { "op": "merge", "kind": "municipality",
              "from": [ ["46", "01"], { "key": ["46", "03"], "then": [
                { "op": "move", "kind": "titleNumber", "key": [12, 3], "to": ["46", "02", 40, 1] } ] } ],
              "into": { "existing": ["46", "02"] } }
        """.trimIndent(),
    )

    private val splitPartNew = obj(
        "SplitPartNew",
        "En ny del, avledet fra kilden. Høyst én del i et `split` har `role`. En `identity`-del er kilden selv og har " +
            "ikke payload (`PayloadOnIdentity`).",
        mapOf(
            "new" to target("Den nye nøkkelen. Full nøkkel fra roten, av samme kind som kilden."),
            "payload" to optionalPayload,
            "role" to inline(
                JsonSchema(
                    type = STRING,
                    description = """
                        Avgjør hva som skjer med kilden. Valgfri.

                        - `identity`: delen er kilden, som flyttes hit og beholder ID-en.
                        - `successor`: delen er ny, avledet fra kilden. Kilden utgår og peker på delen.
                        - Uten `role`: delen er ny, avledet fra kilden. Hva som skjer med kilden, bestemmes av delen med `role`.
                    """.trimIndent(),
                    enum = listOf(GenericElement("identity"), GenericElement("successor")),
                ),
            ),
            "then" to thenOf(ref("CreateOp"), NEW_CONTEXT),
        ),
        listOf("new"),
        """{ "new": ["46", "10"], "role": "successor", "payload": { "navn": "Ny Kommune", "koordinatsystem": "UTM32", "senterpunkt": { "x": 230000.0, "y": 6630000.0 }, "kommunevapen": "iVBORw0KGgo=" } }""",
    )

    private val splitPartExisting = obj(
        "SplitPartExisting",
        "En eksisterende etterfølger som ikke opprettes av delingen. Kilden utgår og peker på delen. Har ikke payload.",
        mapOf(
            "existing" to target("Etterfølgeren. Full nøkkel fra roten, av samme kind som kilden."),
            "role" to const("successor", "Påkrevd. Kilden utgår og peker på delen."),
            "then" to thenOf(ref("OpSpec"), "Barn av delen, relative til delen. Delen står i ro, så alle op-er er lov."),
        ),
        listOf("existing", "role"),
        """{ "existing": ["50", "01"], "role": "successor" }""",
    )

    private val splitPart = JsonSchema(
        title = "SplitPart",
        description = """
            En del i `split.into`: ny (`new`) eller eksisterende (`existing`), aldri begge.

            | del | `role` | delen | kilden |
            |---|---|---|---|
            | `new` | `identity` | er kilden, som flyttes hit og beholder ID-en | flyttes hit |
            | `new` | `successor` | ny, avledet fra kilden | utgår og peker på delen |
            | `existing` | `successor` (påkrevd) | opprettes ikke av noden | utgår og peker på delen |
            | `new` | ingen | ny, avledet fra kilden | bestemmes av delen med `role` |

            Har ingen del `role`, utgår kilden uten etterfølger.
        """.trimIndent(),
        oneOf = listOf(inline(splitPartNew), inline(splitPartExisting)),
    )

    val splitOp = obj(
        "SplitOp",
        """
            Kilden deles i delene i `into`. `role` på delene avgjør hva som skjer med kilden. `"into": []` betyr at
            kilden utgår uten etterfølger og uten nye deler.

            Hvilke roller som gir mening, avhenger av kinden:

            - Kommuner har én peker til etterfølgeren, så en kommune som deles, må ha en del med `successor`.
            - Fylker har ingen slik peker, så et fylke som deles, har ingen del med `role`.
            - En veg som ikke får ny nøkkel, blir slettet. En veg deles derfor med `identity`.
            - Et gårdsnummer utgår med `"into": []`, og bruksnumrene under flyttes enkeltvis i `then`.

            En kommunedeling der etterfølgeren ikke opprettes av delingen (`existing`), er gyldig i formatet, men
            stoppes i steget `bro`.
        """.trimIndent(),
        mapOf(
            "op" to op("split"),
            "kind" to kindProperty,
            "key" to pathKey,
            "into" to list(
                inline(splitPart),
                "Delene kilden deles i. Høyst én del har `role` (`MultipleRoles`). Kan være tom.",
            ),
            "then" to thenOf(sourceOpItems(), SOURCE_CONTEXT),
        ),
        listOf("op", "kind", "key", "into"),
        """
            { "op": "split", "kind": "addressCode", "key": ["46", "05", 1300],
              "into": [ { "new": ["46", "10", 1300], "role": "identity" }, { "new": ["46", "11", 1300] } ] }
        """.trimIndent(),
    )

    val createOp = obj(
        "CreateOp",
        """
            Ny entitet. Uten `derivedFrom` har den ingen kilde, og `payload` er påkrevd (`PayloadRequired`). Med
            `derivedFrom` avledes den fra en kilde som ikke endres av noden. `derivedFrom` sier ingenting om hva som skjer
            med kilden. Det bestemmes av kildens egen node (`split`, `merge` eller `move`). Har kilden ingen egen node,
            fortsetter den uendret (utskilling).

            Utskilling stoppes i steget `bro`, fordi DTO-en ikke kan uttrykke den.
        """.trimIndent(),
        mapOf(
            "op" to op("create"),
            "kind" to kindProperty,
            "key" to pathKey,
            "derivedFrom" to target(
                "Kilden den nye entiteten avledes fra og arver data fra. Full nøkkel fra roten, av samme kind som `key`.",
            ),
            "payload" to described(
                "Data til den nye entiteten. Påkrevd uten `derivedFrom`. Med `derivedFrom` er den valgfri og " +
                    "overstyrer det som arves. Bare for `county` og `municipality`.",
                inline(payload),
            ),
            "then" to thenOf(ref("CreateOp"), NEW_CONTEXT),
        ),
        listOf("op", "kind", "key"),
        """
            { "op": "create", "kind": "municipality", "key": ["46", "10"], "derivedFrom": ["46", "05"],
              "payload": { "navn": "Ny Kommune", "koordinatsystem": "UTM32",
                           "senterpunkt": { "x": 230000.0, "y": 6630000.0 }, "kommunevapen": "iVBORw0KGgo=" } }
        """.trimIndent(),
    )

    val opSpec = JsonSchema(
        title = "OpSpec",
        description = """
            En node i reformtreet. `op` avgjør formen. Alle noder har `op` og `kind`. Barn ligger i `then`, og pivotene
            deres er relative til nøkkelen til noden.

            | op | felter | betydning |
            |---|---|---|
            | `adjust` | `key`, `then`? | Ingen endring. Gir kontekst til barna. |
            | `move` | `key`, `to`, `then`? | Entiteten får ny nøkkel og beholder ID-en. |
            | `merge` | `from`, `into` | Kildene i `from` utgår og peker på målet i `into`. |
            | `split` | `key`, `into`, `then`? | Kilden deles i delene i `into`. |
            | `create` | `key`, `derivedFrom`?, `payload`?, `then`? | Ny entitet. |

            Hvilke op-er som er lov i en `then`-liste, avhenger av hva som skjer med nøkkelen til noden over:

            | kontekst | `then` under | lovlige op-er |
            |---|---|---|
            | står i ro | rotnivået, `adjust`, `merge.into.existing`, `split.into[]` med `existing` | alle |
            | under en kilde | `move`, `split`, elementer i `merge.from` | `move`, `split`, `merge` |
            | under en ny nøkkel | `create`, `merge.into.new`, `split.into[]` med `new` | `create` |

            Skjemaene følger konteksten, så `then` under en kilde bruker `SourceOpSpec` og `then` under en ny nøkkel
            bruker `CreateOp`. En op i feil kontekst gir `OpNotAllowedInContext` i steget `format`.
        """.trimIndent(),
        oneOf = listOf(inline(adjustOp), inline(moveOp), inline(mergeOp), inline(splitOp), inline(createOp)),
        discriminator = opDiscriminator("adjust", "move", "merge", "split", "create"),
    )

    // Hele specen

    val reformSpec = obj(
        "ReformSpec",
        """
            En reform slik frontend skriver og lagrer den: et tre av operasjoner som beskriver hvordan nøkler i
            matrikkelen endres når fylker og kommuner slås sammen, deles eller får nye nummer. Specen har ingen `id`.
            ID-en tildeles når specen lagres. Ukjente felter er en feil (`InvalidShape`). Se `docs/reform-format.md`.
        """.trimIndent(),
        mapOf(
            "format" to inline(
                JsonSchema(
                    type = INTEGER,
                    description = "Versjonen av formatet. Valgfri, standard `1`. Svar fra API-et har alltid med feltet.",
                    default = GenericElement(1),
                ),
            ),
            "navn" to string("Navnet på reformen."),
            "ikrafttredelse" to string(
                "Datoen reformen trer i kraft (`ÅÅÅÅ-MM-DD`). Gjelder også som ikrafttredelsesdato for alle nye kommuner.",
                format = "date",
            ),
            "operasjoner" to list(
                inline(opSpec),
                "Nodene på rotnivå. Konteksten er tom og nøkkelen står i ro, så alle op-er er lov. Pivotene starter " +
                    "med fylket.",
            ),
        ),
        listOf("navn", "ikrafttredelse", "operasjoner"),
    )
}
