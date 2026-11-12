package no.kartverket.komreg.regulering.api.server.route.regulering

import io.ktor.openapi.AdditionalProperties
import io.ktor.openapi.GenericElement
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonSchemaDiscriminator
import io.ktor.openapi.JsonType
import io.ktor.openapi.ReferenceOr
import no.kartverket.komreg.regulering.api.server.route.regulering.ReformSpecSchemas.described
import no.kartverket.komreg.regulering.api.server.route.regulering.ReformSpecSchemas.inline
import no.kartverket.komreg.regulering.api.server.route.regulering.ReformSpecSchemas.integer
import no.kartverket.komreg.regulering.api.server.route.regulering.ReformSpecSchemas.list
import no.kartverket.komreg.regulering.api.server.route.regulering.ReformSpecSchemas.nullableObject
import no.kartverket.komreg.regulering.api.server.route.regulering.ReformSpecSchemas.obj
import no.kartverket.komreg.regulering.api.server.route.regulering.ReformSpecSchemas.string

/** Håndskrevne JSON-skjemaer for svarene fra `/api/reguleringer` som ikke er selve specen. */
internal object ReguleringSchemas {
    val reformFeil = obj(
        "ReformFeil",
        """
            En feil i en reform-spec. `sti` peker på stedet i specen, og `steg` sier hvilket steg i valideringskjeden
            som fant feilen. Stegene kjøres i rekkefølge, og kjeden stopper i det første steget som gir feil. Se
            `docs/reform-format.md`.
        """.trimIndent(),
        mapOf(
            "sti" to string(
                "JSON-sti til stedet i specen, for eksempel `$.operasjoner[0].then[1].to`. `$` er hele specen.",
            ),
            "steg" to inline(
                JsonSchema(
                    type = JsonType.STRING,
                    description = """
                        Steget som fant feilen:

                        - `form`: JSON-en har ikke formen formatet krever (`InvalidShape`).
                        - `format`: reglene i formatet er brutt, som ukjent kind, ugyldig segment eller ulovlig op i konteksten.
                        - `konflikt`: reglene for nøklene motsier hverandre.
                        - `domene`: reformen er ikke en regulering matrikkelen støtter.
                        - `data`: specen stemmer ikke med dataene i matrikkelen.
                        - `bro`: reformen lar seg ikke uttrykke i DTO-en. Bare i `GET /{id}/regulering`.
                    """.trimIndent(),
                    enum = listOf("form", "format", "konflikt", "domene", "data", "bro").map { GenericElement(it) },
                ),
            ),
            "kode" to string(
                """
                    Stabil kode for feilen. Bruk den, ikke `melding`, i frontend-logikk. Listen kan utvides, så tål ukjente koder.
                    Kjente koder per steg:

                    - `form`: `InvalidShape`
                    - `format`: `UnknownKind`, `OpNotAllowedInContext`, `KindNotChildOfContext`, `InvalidSegment`,
                      `TargetLengthMismatch`, `MultipleRoles`, `PayloadOnIdentity`, `EmptyMerge`, `PayloadRequired`,
                      `PayloadNotAllowed`, `InvalidPayload`, `KeptAndConsumed`
                    - `konflikt`: `Diverges`, `Conflict`, `SourceIsTarget`, `TargetUnderSource`,
                      `SourceUnderCreatedTarget`, `DeriveFromTarget`
                    - `domene`: `ActionNotSupported`, `RetireNeedsSuccessor`, `RetireHasSuccessor`, `WrongPayload`,
                      `MissingMunicipalityCoat`, `TargetsInMultipleCounties`, `DeriveWithoutMove`,
                      `SeriesRetireWithoutTitleMoves`, `ParcelHasCadastralNumber`, `ParcelNotPaired`,
                      `TargetTypeMismatch`, `UnsupportedIdentType`
                    - `data`: `MunicipalityNotCovered`, `TargetNotFound`, `TargetExpired`, `TargetInUse`,
                      `SourceNotFound`, `SourceExpired`
                    - `bro`: `IngenDtoEkvivalent`, `FinnesIkke`, `UgyldigPayload`, `UlikTransformasjon`
                """.trimIndent(),
            ),
            "melding" to string("Forklaring til mennesker."),
            "hint" to string("Forslag til hvordan feilen kan rettes. Utelates når det ikke finnes noe.", nullable = true),
            "flereSti" to list(
                string(),
                "Andre steder i specen feilen gjelder, for eksempel de to nodene som er i konflikt. Utelates når den er tom.",
            ),
        ),
        listOf("sti", "steg", "kode", "melding"),
        """
            { "sti": "$.operasjoner[0].to", "steg": "format", "kode": "TargetLengthMismatch",
              "melding": "Målet må være en full nøkkel fra roten.", "flereSti": [] }
        """.trimIndent(),
    )

    val valideringResultat = obj(
        "ValideringResultat",
        """
            Resultatet av å validere en spec. `gyldig` er `true` hvis og bare hvis `feil` er tom. Kjeden stopper i det
            første steget som gir feil, så `feil` inneholder feilene fra ett steg.
        """.trimIndent(),
        mapOf(
            "gyldig" to inline(JsonSchema(type = JsonType.BOOLEAN, description = "`true` når specen er gyldig.")),
            "feil" to list(inline(reformFeil), "Feilene som ble funnet. Tom når specen er gyldig."),
        ),
        listOf("gyldig", "feil"),
        """{ "gyldig": true, "feil": [] }""",
    )

    val opprettetRegulering = obj(
        "OpprettetRegulering",
        "Svaret når en reform er lagret.",
        mapOf("id" to integer("ID-en reformen ble lagret med. Bruk den i `/api/reguleringer/{id}`.")),
        listOf("id"),
        """{ "id": 7 }""",
    )

    val reformSammendrag = obj(
        "ReformSammendrag",
        "Sammendrag av en lagret reform. Hele specen hentes med `GET /api/reguleringer/{id}`.",
        mapOf(
            "id" to integer("ID-en til reformen."),
            "navn" to string("Navnet på reformen."),
            "ikrafttredelse" to string("Datoen reformen trer i kraft (`ÅÅÅÅ-MM-DD`).", format = "date"),
        ),
        listOf("id", "navn", "ikrafttredelse"),
        """{ "id": 7, "navn": "Kommunereform 2024", "ikrafttredelse": "2024-01-01" }""",
    )

    val reformSammendragListe = JsonSchema(
        type = JsonType.ARRAY,
        description = "Alle lagrede reformer, sortert på `id`. Tom når ingen er lagret.",
        items = inline(reformSammendrag),
    )

    // Regulering-DTO. Den som kjører reguleringen, `server`, leser den samme formen.

    private fun nr(description: String) = string(description)

    private val fraTil = obj(
        "FraTilDTO",
        "En nøkkelkomponent som skifter verdi, fra `fra` til `til`.",
        mapOf("fra" to string("Verdien før reguleringen."), "til" to string("Verdien etter reguleringen.")),
        listOf("fra", "til"),
        """{ "fra": "01", "til": "02" }""",
    )

    private val fraEnTilMange = obj(
        "FraEnTilMangeDTO",
        "En nøkkelkomponent som går fra én verdi til én eller flere. Ved deling står etterfølgeren først i `til`.",
        mapOf(
            "fra" to string("Verdien før reguleringen."),
            "til" to list(string(), "Verdiene etter reguleringen. Etterfølgeren står først."),
        ),
        listOf("fra", "til"),
        """{ "fra": "01", "til": ["01", "10"] }""",
    )

    private val fylke = obj(
        "FylkeDTO",
        "Et fylke.",
        mapOf("navn" to string("Navnet på fylket."), "fylkesnummer" to nr("Fylkesnummeret, 2 siffer.")),
        listOf("navn", "fylkesnummer"),
        """{ "navn": "Vestland", "fylkesnummer": "46" }""",
    )

    private val enkelKommune = obj(
        "EnkelKommuneDTO",
        "En kommune som utgår, bare med navn og nummer.",
        mapOf(
            "navn" to string("Navnet på kommunen."),
            "fylkesnummer" to nr("Fylkesnummeret, 2 siffer."),
            "kommunenummer" to nr("Kommunens løpenummer i fylket, 2 siffer."),
        ),
        listOf("navn", "fylkesnummer", "kommunenummer"),
        """{ "navn": "Bergen", "fylkesnummer": "46", "kommunenummer": "01" }""",
    )

    private val koordinatsystem = JsonSchema(
        type = JsonType.STRING,
        title = "KoordinatsystemDTO",
        description = "Koordinatsystemet et senterpunkt er oppgitt i.",
        enum = listOf("UTM32", "UTM33", "UTM35", "UKJENT").map { GenericElement(it) },
    )

    private val koordinat = obj(
        "KoordinatDTO",
        "Et punkt i et koordinatsystem. `x` er østing og `y` er nording.",
        mapOf(
            "x" to inline(JsonSchema(type = JsonType.NUMBER, format = "double")),
            "y" to inline(JsonSchema(type = JsonType.NUMBER, format = "double")),
        ),
        listOf("x", "y"),
    )

    private val gardsnummerserie = obj(
        "GardsnummerserieDTO",
        "En serie med gårdsnummer fra og med `fra` til og med `til`.",
        mapOf("fra" to integer(), "til" to integer()),
        listOf("fra", "til"),
    )

    private val adresse = obj(
        "AdresseDTO",
        "Postadressen til en kommune.",
        mapOf(
            "adresselinje1" to string(nullable = true),
            "adresselinje2" to string(nullable = true),
            "postnummer" to string(),
            "poststed" to string("Tom for en ny kommune. Poststedet fylles ut av matrikkelen."),
        ),
        listOf("adresselinje1", "adresselinje2", "postnummer", "poststed"),
    )

    private val standardRekvirent = obj(
        "StandardRekvirentDTO",
        "Standard rekvirent for kommunen.",
        mapOf("orgnummer" to string(), "navn" to string("Tomt for en ny kommune.")),
        listOf("orgnummer", "navn"),
    )

    private val kommune = obj(
        "KommuneDTO",
        "En ny kommune med alle dataene den opprettes med. Bygges av `payload` i specen og nøklene.",
        mapOf(
            "navn" to string("Navnet på kommunen."),
            "fylkesnummer" to nr("Fylkesnummeret, 2 siffer."),
            "kommunenummer" to nr("Kommunens løpenummer i fylket, 2 siffer."),
            "gyldigTilDato" to string("Alltid `null` for en ny kommune.", format = "date", nullable = true),
            "koordinatsystem" to described("Koordinatsystemet til `senterpunkt`.", inline(koordinatsystem)),
            "senterpunkt" to described("Senterpunktet til kommunen.", inline(koordinat)),
            "nedsattKonsesjonsgrense" to inline(JsonSchema(type = JsonType.BOOLEAN)),
            "godkjenteGardsnumre" to list(inline(gardsnummerserie), "Serier med godkjente gårdsnumre."),
            "adresse" to nullableObject("Postadressen. `null` når kommunen ikke har en.", adresse),
            "standardRekvirent" to nullableObject("Standard rekvirent. `null` når kommunen ikke har en.", standardRekvirent),
            "kommunevapen" to string("Kommunevåpenet som base64.", format = "byte", nullable = true),
        ),
        listOf(
            "navn", "fylkesnummer", "kommunenummer", "gyldigTilDato", "koordinatsystem", "senterpunkt",
            "nedsattKonsesjonsgrense", "godkjenteGardsnumre", "adresse", "standardRekvirent", "kommunevapen",
        ),
    )

    private val tilGrunneiendom = obj(
        "TilGrunneiendomDTO",
        "Grunneiendommen et bruksnummer havner på når gårdsnummeret utgår.",
        mapOf(
            "fylkesnummer" to inline(JsonSchema(type = JsonType.INTEGER, format = "int64")),
            "kommunelopenummer" to inline(JsonSchema(type = JsonType.INTEGER, format = "int32")),
            "gardsnummer" to inline(JsonSchema(type = JsonType.INTEGER, format = "int32")),
            "bruksnummer" to inline(JsonSchema(type = JsonType.INTEGER, format = "int32")),
        ),
        listOf("bruksnummer"),
        """{ "fylkesnummer": 46, "kommunelopenummer": 2, "gardsnummer": 40, "bruksnummer": 1 }""",
    )

    private fun fraTilRef(description: String) = described(description, inline(fraTil))

    private fun fraEnTilMangeRef(description: String) = described(description, inline(fraEnTilMange))

    private fun transformasjon(
        name: String,
        type: String,
        description: String,
        properties: Map<String, ReferenceOr<JsonSchema>>,
        required: List<String>,
        example: String,
    ) = obj(
        name,
        description,
        mapOf("type" to ReformSpecSchemas.const(type, "Typen transformasjon.")) + properties,
        listOf("type") + required,
        example,
    )

    private val sammenslaa = inline(
        JsonSchema(
            type = ReformSpecSchemas.nullable(JsonType.BOOLEAN),
            description = "`true` når dette ikke er den første kilden til samme mål. Utelates når `false`.",
        ),
    )

    private val fylkeTransformasjon = transformasjon(
        "FylkeTransformasjonDTO", "fylke", "Et fylkesnummer som endres.",
        mapOf("fylkesnummer" to fraEnTilMangeRef("Fylkesnummeret før og etter."), "sammenslaa" to sammenslaa),
        listOf("fylkesnummer"),
        """{ "type": "fylke", "fylkesnummer": { "fra": "50", "til": ["46"] } }""",
    )

    private val kommuneTransformasjon = transformasjon(
        "KommuneTransformasjonDTO", "kommune", "En kommune som får nytt løpenummer, deles eller slås sammen.",
        mapOf(
            "fylkesnummer" to fraEnTilMangeRef("Fylkesnummeret før og etter."),
            "kommuneløpenummer" to fraEnTilMangeRef("Løpenummeret før og etter."),
            "sammenslaa" to sammenslaa,
        ),
        listOf("fylkesnummer", "kommuneløpenummer"),
        """{ "type": "kommune", "fylkesnummer": { "fra": "50", "til": ["46"] }, "kommuneløpenummer": { "fra": "01", "til": ["02"] } }""",
    )

    private val matrikkelenhetTransformasjon = transformasjon(
        "MatrikkelenhetTransformasjonDTO", "matrikkelenhet",
        "Et gårdsnummer som endres. `gårdsnummer.til` er `\"0\"` når serien utgår. Bruksnumrene under flyttes da " +
            "enkeltvis i `bruksnummer`.",
        mapOf(
            "fylkesnummer" to fraTilRef("Fylkesnummeret før og etter."),
            "kommuneløpenummer" to fraTilRef("Løpenummeret før og etter."),
            "gårdsnummer" to fraTilRef("Gårdsnummeret før og etter."),
            "bruksnummer" to inline(
                JsonSchema(
                    type = ReformSpecSchemas.nullable(JsonType.OBJECT),
                    description = "Bruksnumre som flyttes enkeltvis, med bruksnummeret før som nøkkel.",
                    additionalProperties = AdditionalProperties.PSchema(inline(tilGrunneiendom)),
                ),
            ),
        ),
        listOf("fylkesnummer", "kommuneløpenummer", "gårdsnummer"),
        """{ "type": "matrikkelenhet", "fylkesnummer": { "fra": "46", "til": "46" }, "kommuneløpenummer": { "fra": "01", "til": "02" }, "gårdsnummer": { "fra": "40", "til": "40" } }""",
    )

    private val kretsTransformasjon = transformasjon(
        "KretsTransformasjonDTO", "krets", "En krets som endres.",
        mapOf(
            "fylkesnummer" to fraTilRef("Fylkesnummeret før og etter."),
            "kommuneløpenummer" to fraTilRef("Løpenummeret før og etter."),
            "kretsnummer" to fraTilRef("Kretsnummeret før og etter."),
            "kretstype" to fraTilRef("Kretstypen før og etter."),
        ),
        listOf("fylkesnummer", "kommuneløpenummer", "kretsnummer", "kretstype"),
        """{ "type": "krets", "fylkesnummer": { "fra": "46", "til": "46" }, "kommuneløpenummer": { "fra": "01", "til": "02" }, "kretsnummer": { "fra": "3", "til": "3" }, "kretstype": { "fra": "valg", "til": "valg" } }""",
    )

    private val vegTransformasjon = transformasjon(
        "VegTransformasjonDTO", "veg", "En veg (adressekode) som endres.",
        mapOf(
            "fylkesnummer" to fraEnTilMangeRef("Fylkesnummeret før og etter."),
            "kommuneløpenummer" to fraEnTilMangeRef("Løpenummeret før og etter."),
            "adressekode" to fraEnTilMangeRef("Adressekoden før og etter."),
        ),
        listOf("fylkesnummer", "kommuneløpenummer", "adressekode"),
        """{ "type": "veg", "fylkesnummer": { "fra": "46", "til": ["46"] }, "kommuneløpenummer": { "fra": "01", "til": ["02"] }, "adressekode": { "fra": "1234", "til": ["1234"] } }""",
    )

    private val teigTransformasjon = transformasjon(
        "TeigTransformasjonDTO", "teig", "En teig som endres.",
        mapOf(
            "fylkesnummer" to fraTilRef("Fylkesnummeret før og etter."),
            "kommuneløpenummer" to fraTilRef("Løpenummeret før og etter."),
            "teigId" to fraTilRef("Teig-ID-en før og etter."),
        ),
        listOf("fylkesnummer", "kommuneløpenummer", "teigId"),
        """{ "type": "teig", "fylkesnummer": { "fra": "46", "til": "46" }, "kommuneløpenummer": { "fra": "01", "til": "02" }, "teigId": { "fra": "987654", "til": "987654" } }""",
    )

    private val vegadresseTransformasjon = transformasjon(
        "VegadresseTransformasjonDTO", "vegadresse", "En vegadresse som endres.",
        mapOf(
            "fylkesnummer" to fraTilRef("Fylkesnummeret før og etter."),
            "kommuneløpenummer" to fraTilRef("Løpenummeret før og etter."),
            "adressekode" to fraTilRef("Adressekoden før og etter."),
            "adressenummer" to fraTilRef("Adressenummeret før og etter."),
        ),
        listOf("fylkesnummer", "kommuneløpenummer", "adressekode", "adressenummer"),
        """{ "type": "vegadresse", "fylkesnummer": { "fra": "46", "til": "46" }, "kommuneløpenummer": { "fra": "01", "til": "02" }, "adressekode": { "fra": "1234", "til": "1234" }, "adressenummer": { "fra": "5", "til": "5" } }""",
    )

    private val transformasjonDto = JsonSchema(
        title = "TransformasjonDTO",
        description = "En endring av én nøkkel. `type` avgjør formen.",
        oneOf = listOf(
            fylkeTransformasjon, kommuneTransformasjon, matrikkelenhetTransformasjon, kretsTransformasjon,
            vegTransformasjon, teigTransformasjon, vegadresseTransformasjon,
        ).map(::inline),
        discriminator = JsonSchemaDiscriminator(
            propertyName = "type",
            mapping = listOf(
                "fylke" to "FylkeTransformasjonDTO",
                "kommune" to "KommuneTransformasjonDTO",
                "matrikkelenhet" to "MatrikkelenhetTransformasjonDTO",
                "krets" to "KretsTransformasjonDTO",
                "veg" to "VegTransformasjonDTO",
                "teig" to "TeigTransformasjonDTO",
                "vegadresse" to "VegadresseTransformasjonDTO",
            ).associate { (type, name) -> type to "#/components/schemas/$name" },
        ),
    )

    private val endring = obj(
        "EndringDTO",
        "Én endring i en regulering: fylker og kommuner som utgår eller opprettes, og transformasjonene av nøklene.",
        mapOf(
            "id" to string("ID-en til endringen."),
            "navn" to string("Navnet på endringen."),
            "type" to inline(
                JsonSchema(
                    type = JsonType.STRING,
                    description = "`fylke` når endringen har fylketransformasjoner, ellers `kommune`.",
                    enum = listOf(GenericElement("fylke"), GenericElement("kommune")),
                ),
            ),
            "utgåendeFylker" to list(inline(fylke), "Fylker som utgår."),
            "utgåendeKommuner" to list(inline(enkelKommune), "Kommuner som utgår."),
            "nyeFylker" to list(inline(fylke), "Fylker som opprettes."),
            "nyeKommuner" to list(inline(kommune), "Kommuner som opprettes."),
            "transformasjoner" to list(inline(transformasjonDto), "Endringene av nøkler."),
        ),
        listOf(
            "id", "navn", "type", "utgåendeFylker", "utgåendeKommuner", "nyeFylker", "nyeKommuner", "transformasjoner",
        ),
    )

    val regulering = obj(
        "Regulering",
        """
            Reformen oversatt til regulering-DTO-en, formen reguleringen kjøres fra. Den lages av den lagrede specen hver
            gang den hentes, og lagres ikke. Fylker og kommuner som slås sammen eller deles, og matrikkelenheter, veger,
            kretser, teiger og adresser som følger med, ligger i `transformasjoner`.
        """.trimIndent(),
        mapOf(
            "id" to string("ID-en til reformen, som tekst."),
            "navn" to string("Navnet på reformen."),
            "dato" to string("Datoen reguleringen trer i kraft (`ÅÅÅÅ-MM-DD`).", format = "date"),
            "endringer" to list(inline(endring), "Endringene i reguleringen."),
        ),
        listOf("id", "navn", "dato", "endringer"),
    )
}
