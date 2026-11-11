package no.kartverket.komreg.regulering.api.server.route.regulering

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.ExampleObject
import io.ktor.openapi.GenericElement
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.Operation
import io.ktor.openapi.Responses
import io.ktor.server.routing.openapi.RouteOperationFunction
import kotlinx.serialization.json.Json

/** OpenAPI-beskrivelsene til endepunktene under `/api/reguleringer`. Skjemaene ligger i [ReformSpecSchemas] og [ReguleringSchemas]. */
internal object ReguleringOpenApi {
    const val TAG = "Reguleringer"
    const val TAG_DESCRIPTION =
        "Reformer slik frontend skriver og lagrer dem (reform-formatet i `docs/reform-format.md`), og reguleringen " +
            "de gir."

    private fun example(json: String) = GenericElement(Json.parseToJsonElement(json))

    private val idDescription = "ID-en til reformen, slik den ble gitt da reformen ble lagret."

    private val specEksempler = mapOf(
        "flytting" to ExampleObject(
            summary = "Flytting av veg og teig",
            description = "En veg og en teig flyttes til en annen kommune. Vegadressen under vegen får nytt nummer.",
            value = example(
                """
                { "format": 1, "navn": "Flytting av veg og teig", "ikrafttredelse": "2027-01-01",
                  "operasjoner": [
                    { "op": "move", "kind": "addressCode", "key": ["46", "01", 1234], "to": ["46", "02", 1234],
                      "then": [ { "op": "move", "kind": "addressNumber", "key": 5, "to": ["46", "02", 1234, 6] } ] },
                    { "op": "move", "kind": "parcel", "key": ["46", "01", 987654], "to": ["46", "02", 987654] } ] }
                """.trimIndent(),
            ),
        ),
        "deling" to ExampleObject(
            summary = "Deling av kommune og oppløsning av gårdsnummerserie",
            description = "Kommune 4605 deles i 4610 (etterfølger) og 4611. Veg 1234 flyttes til 4611, og veg 1300 deles. " +
                "Gårdsnummer 4601-12 utgår, og bruksnummer 1 flyttes til 4601-13/1.",
            value = example(
                """
                { "format": 1, "navn": "Deling av kommune og oppløsning av gårdsnummerserie", "ikrafttredelse": "2027-01-01",
                  "operasjoner": [
                    { "op": "split", "kind": "municipality", "key": ["46", "05"],
                      "into": [
                        { "new": ["46", "10"], "role": "successor",
                          "payload": { "navn": "Nye Kommune", "koordinatsystem": "UTM32",
                                       "senterpunkt": { "x": 230000.0, "y": 6630000.0 },
                                       "godkjenteGardsnumre": [ { "fra": 1, "til": 200 } ], "kommunevapen": "iVBORw0KGgo=" } },
                        { "new": ["46", "11"],
                          "payload": { "navn": "Nye Kommune", "koordinatsystem": "UTM32",
                                       "senterpunkt": { "x": 230000.0, "y": 6630000.0 },
                                       "godkjenteGardsnumre": [ { "fra": 1, "til": 200 } ], "kommunevapen": "iVBORw0KGgo=" } } ],
                      "then": [
                        { "op": "move", "kind": "addressCode", "key": 1234, "to": ["46", "11", 1234] },
                        { "op": "split", "kind": "addressCode", "key": 1300,
                          "into": [ { "new": ["46", "10", 1300], "role": "identity" }, { "new": ["46", "11", 1300] } ] } ] },
                    { "op": "split", "kind": "landNumber", "key": ["46", "01", 12], "into": [],
                      "then": [ { "op": "move", "kind": "titleNumber", "key": 1, "to": ["46", "01", 13, 1] } ] } ] }
                """.trimIndent(),
            ),
        ),
        "nyttFylke" to ExampleObject(
            summary = "Sammenslåing til nytt fylke og omnummerering av kommune",
            description = "Fylkene 30 og 31 slås sammen til det nye fylket 33, som får en ny kommune. Kommune 4601 får " +
                "nummer 4699.",
            value = example(
                """
                { "format": 1, "navn": "Sammenslåing til nytt fylke", "ikrafttredelse": "2027-01-01",
                  "operasjoner": [
                    { "op": "merge", "kind": "county", "from": ["30", "31"],
                      "into": { "new": ["33"], "payload": { "navn": "Buskerud" }, "then": [
                        { "op": "create", "kind": "municipality", "key": "01",
                          "payload": { "navn": "Nye Kommune", "koordinatsystem": "UTM32",
                                       "senterpunkt": { "x": 230000.0, "y": 6630000.0 },
                                       "godkjenteGardsnumre": [ { "fra": 1, "til": 200 } ], "kommunevapen": "iVBORw0KGgo=" } } ] } },
                    { "op": "merge", "kind": "municipality", "from": [["46", "01"]],
                      "into": { "new": ["46", "99"],
                                "payload": { "navn": "Omnummerert", "koordinatsystem": "UTM32",
                                             "senterpunkt": { "x": 230000.0, "y": 6630000.0 }, "kommunevapen": "iVBORw0KGgo=" } } } ] }
                """.trimIndent(),
            ),
        ),
    )

    private val ugyldigSpecEksempler = mapOf(
        "ugyldigSegment" to ExampleObject(
            summary = "Ugyldig segment",
            value = example(
                """
                { "gyldig": false,
                  "feil": [ { "sti": "$.operasjoner[0].key[0]", "steg": "format", "kode": "InvalidSegment",
                              "melding": "Ugyldig verdi 3 for county: …", "hint": "Fylkesnummer er en streng på 2 siffer, for eksempel \"03\".",
                              "flereSti": ["$.operasjoner[2]"] } ] }
                """.trimIndent(),
            ),
        ),
    )

    private val tekstSchema = JsonSchema(type = JsonType.STRING)

    private fun operasjon(
        summary: String,
        description: String,
        configure: Operation.Builder.() -> Unit,
    ): RouteOperationFunction = {
        this.summary = summary
        this.description = description
        tag(TAG)
        configure()
    }

    private fun Operation.Builder.idParameter() {
        parameters {
            path("id") {
                description = idDescription
                required = true
                schema = JsonSchema(type = JsonType.INTEGER, format = "int32", minimum = 1.0, example = example("7"))
            }
        }
    }

    private fun Operation.Builder.specBody(description: String) {
        requestBody {
            this.description = description
            required = true
            ContentType.Application.Json {
                schema = ReformSpecSchemas.reformSpec
                specEksempler.forEach { (name, value) -> example(name, value) }
            }
        }
    }

    private fun Responses.Builder.ugyldigId() {
        HttpStatusCode.BadRequest {
            description = "`id` i stien er ikke et heltall. Svaret er teksten `Ugyldig id`."
            ContentType.Text.Plain {
                schema = tekstSchema
                example("ugyldigId", ExampleObject(value = example("\"Ugyldig id\"")))
            }
        }
    }

    private fun Responses.Builder.ikkeFunnet() {
        HttpStatusCode.NotFound {
            description = "Det finnes ingen reform med denne `id`-en. Svaret har ingen body."
        }
    }

    private fun Responses.Builder.ugyldigSpec(description: String) {
        HttpStatusCode.UnprocessableEntity {
            this.description = description
            ContentType.Application.Json {
                schema = ReguleringSchemas.valideringResultat
                ugyldigSpecEksempler.forEach { (name, value) -> example(name, value) }
            }
        }
    }

    val valider: RouteOperationFunction = operasjon(
        summary = "Valider en reform",
        description = """
            Validerer en reform-spec uten å lagre den. Specen kjøres gjennom stegene `form`, `format`, `konflikt`,
            `domene` og `data`, og kjeden stopper i det første steget som gir feil.

            Svaret er alltid `200`, også når specen er ugyldig. Se `gyldig` og `feil` i svaret. (`POST /api/reguleringer`
            og `PUT /api/reguleringer/{id}` svarer `422` for samme feil.) Steget `bro` sjekkes ikke her, bare i
            `GET /api/reguleringer/{id}/regulering`.
        """.trimIndent(),
    ) {
        specBody("Reformen som skal valideres, i formatet fra `docs/reform-format.md`.")
        responses {
            HttpStatusCode.OK {
                description = "Valideringsresultatet. `gyldig` er `false` og `feil` er fylt ut når specen er ugyldig."
                ContentType.Application.Json {
                    schema = ReguleringSchemas.valideringResultat
                    example("gyldig", ExampleObject(summary = "Gyldig spec", value = example("""{ "gyldig": true, "feil": [] }""")))
                    ugyldigSpecEksempler.forEach { (name, value) -> example(name, value) }
                }
            }
        }
    }

    val opprett: RouteOperationFunction = operasjon(
        summary = "Opprett en reform",
        description = """
            Validerer reform-specen og lagrer den. Specen lagres slik den er skrevet, og får en ny `id`. Er specen
            ugyldig, lagres ingenting, og svaret er `422` med feilene.
        """.trimIndent(),
    ) {
        specBody("Reformen som skal lagres, i formatet fra `docs/reform-format.md`.")
        responses {
            HttpStatusCode.Created {
                description = "Reformen er lagret."
                ContentType.Application.Json { schema = ReguleringSchemas.opprettetRegulering }
            }
            ugyldigSpec("Specen er ugyldig og er ikke lagret. `gyldig` er alltid `false`.")
        }
    }

    val list: RouteOperationFunction = operasjon(
        summary = "List reformer",
        description = "Lister alle lagrede reformer med `id`, `navn` og `ikrafttredelse`, sortert på `id`. Hele specen hentes med `GET /api/reguleringer/{id}`.",
    ) {
        responses {
            HttpStatusCode.OK {
                description = "Alle lagrede reformer. Listen er tom når ingen er lagret."
                ContentType.Application.Json { schema = ReguleringSchemas.reformSammendragListe }
            }
        }
    }

    val hent: RouteOperationFunction = operasjon(
        summary = "Hent en reform",
        description = "Henter specen slik den ble lagret. Feltene `then`, `payload` og `derivedFrom` utelates når de er tomme, og `format` er alltid med.",
    ) {
        idParameter()
        responses {
            HttpStatusCode.OK {
                description = "Reform-specen."
                ContentType.Application.Json { schema = ReformSpecSchemas.reformSpec }
            }
            ugyldigId()
            ikkeFunnet()
        }
    }

    val oppdater: RouteOperationFunction = operasjon(
        summary = "Erstatt en reform",
        description = """
            Validerer reform-specen og erstatter den lagrede specen med den. `id` beholdes. Specen valideres før `id`
            slås opp, så en ugyldig spec gir `422` også når reformen ikke finnes.
        """.trimIndent(),
    ) {
        idParameter()
        specBody("Den nye reformen, i formatet fra `docs/reform-format.md`.")
        responses {
            HttpStatusCode.NoContent { description = "Reformen er erstattet. Svaret har ingen body." }
            ugyldigId()
            ikkeFunnet()
            ugyldigSpec("Specen er ugyldig og reformen er ikke endret. `gyldig` er alltid `false`.")
        }
    }

    val slett: RouteOperationFunction = operasjon(
        summary = "Slett en reform",
        description = "Sletter den lagrede reformen.",
    ) {
        idParameter()
        responses {
            HttpStatusCode.NoContent { description = "Reformen er slettet. Svaret har ingen body." }
            ugyldigId()
            ikkeFunnet()
        }
    }

    val regulering: RouteOperationFunction = operasjon(
        summary = "Hent reguleringen til en reform",
        description = """
            Oversetter den lagrede reformen til `Regulering`-DTO-en som `server` kjører. Specen valideres på nytt først,
            siden dataene i matrikkelen kan ha endret seg siden den ble lagret, og siden DTO-en ikke kan uttrykke alt
            formatet kan (steget `bro`). Reguleringen lagres ikke og sendes ikke til `server`.
        """.trimIndent(),
    ) {
        idParameter()
        responses {
            HttpStatusCode.OK {
                description = "Reguleringen. `id` er reformens `id` som tekst."
                ContentType.Application.Json { schema = ReguleringSchemas.regulering }
            }
            ugyldigId()
            ikkeFunnet()
            ugyldigSpec("Reformen er ikke lenger gyldig, eller kan ikke uttrykkes som en regulering (steget `bro`).")
        }
    }
}
