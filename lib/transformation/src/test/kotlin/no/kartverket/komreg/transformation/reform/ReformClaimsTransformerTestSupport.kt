package no.kartverket.komreg.transformation.reform

import arrow.core.getOrElse
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.IdType
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.regulering.reform.domain.type.NumberingScheme
import no.kartverket.komreg.regulering.reform.format.ReformSpec
import no.kartverket.komreg.regulering.reform.format.SegmentRegistry
import no.kartverket.komreg.regulering.reform.format.resolve
import no.kartverket.komreg.regulering.reform.format.v1
import no.kartverket.komreg.regulering.reform.rules.validateDomain
import no.kartverket.komreg.transformation.createDummyKommunevaapenImage
import no.kartverket.komreg.transformation.dummyId
import no.kartverket.komreg.transformation.dummyKommune
import no.kartverket.komreg.transformation.tilKommunedata
import java.util.Base64

/*
 * Felles støtte for `ReformClaimsTransformer*Test`. Testene er reform-ekvivalenter til:
 *
 *   LegacyIdentTransformerTest          -> ReformClaimsTransformerTest
 *   LegacyIdentTransformerBygningTest   -> ReformClaimsTransformerBygningTest
 *   LegacyIdentTransformerVegTest       -> ReformClaimsTransformerVegTest
 *   LegacyIdentTransformerVegadresseTest-> ReformClaimsTransformerVegadresseTest
 *   LegacyTransformEntityTest           -> ReformClaimsTransformerEntityTest
 *
 * ReformClaims bygges alltid via JSON -> [ReformSpec] -> `resolve` -> `ReformClaims.validate` -> `validateDomain`,
 * aldri med `reform { }` eller `LegacyIdentTransformer.Mapping`. Reglene skrives altså i formatet beskrevet i
 * `docs/reform-format.md`. Domenereglene kjøres, så testene bruker bare reformer som sinkene støtter.
 * Datareglene (mot Matrikkel API) kjøres ikke.
 *
 * ## Avvik fra legacy-testene
 *
 * Reglene i `ReformClaimsTransformer` har ikke helt samme semantikk som `LegacyIdentTransformer`. Avvikene er
 * dokumentert her, og testene viser til dem med kodene A1-A11.
 *
 * **A1. Reglene er en spec, ikke mappinger.** Legacy bygger `LegacyIdentTransformer.Mapping` (eller `mapInput`
 * fra `Reguleringsinput`). Reform bygger en `ReformSpec` i JSON. Fylke og kommune skrives som strenger med
 * ledende null (`"02"`, `"05"`), og gårdsnummer, adressekode osv. som tall. Spec-en har fast
 * ikrafttredelsesdato [IKRAFTTREDELSE]. Legacy-testene brukte `LocalDate.now()`.
 *
 * **A2. `Ident.Empty` blir `null`, og bare for fylker.** Legacy-testen `splittKommuneUtenKobling` lot kommunen
 * utgå uten etterfølger (`Ident.Empty` først i `Split`). `ReformClaimsTransformer` gir `transformedIdent = null`
 * for `Retire` uten `successor` (`split` uten `role`). Domenereglene avviser det for kommuner
 * (`RetireNeedsSuccessor`: kommunen har én peker til etterfølgeren), men godtar det for fylker. Testen «kommune
 * splittes uten ny kommune-kobling» er derfor skrevet om til et fylke som deles. Bygningstestene som brukte en
 * kommunedeling uten etterfølger, deler kommunen med `role: successor`. Bygningen får samme delvise nøkkel, se A8.
 *
 * **A3. Payload er `Kommunedata`.** Legacy-testene brukte en egen `MockKommuneinfo` (og i entitetstesten
 * `Kommune.tilKommunedata`). Reform dekoder `KommunePayload` fra spec-en til `Kommunedata`: navn skrives med
 * store bokstaver, `ikrafttredelsesdato` kommer fra spec-en og kommunevåpen er base64 i JSON. Forventet verdi
 * lages av [forventetKommunedata], og tilsvarer `Kommune.tilKommunedata` for [dummyKommune].
 *
 * **A4. `sammenslaaing` er alltid `null`.** `LegacyIdentTransformer` setter `false` eller `true`.
 * `ReformClaimsTransformer` setter `null`. Ingen av legacy-testene sjekket flagget, og det sjekkes heller ikke her.
 *
 * **A5. `Mapping.Replace` og `Mapping.Simple` blir `merge` og `move`.**
 * - Kommune som får nytt nummer: `merge` med én kilde og `into.new` gir `RetireTo` og `Derive`. Det er de to
 *   transformasjonene legacy lagde (utgått kommune med ny nøkkel, og ny kommune med payload), i samme
 *   rekkefølge.
 * - Veg, vegadresse, gårdsnummer: `move`. Legacy `Replace` på en identtype uten kommunenivå ga to like
 *   assosierte identer som ble slått sammen i et sett. `move` gir bare én.
 *
 * **A6. `Mapping.Split` blir `split`, og `role` avgjør hva legacy-rekkefølgen betydde.**
 * Legacy: første del i `into` fikk entitetens ID, resten fikk nye ID-er, og første mål var etterfølger.
 * - Kommune (legacy: første mål er etterfølger): første del har `role: successor`. Første transformasjon har
 *   samme ID og `transformedIdent = etterfølgeren`, uten payload. Deretter én ny entitet per del.
 * - Kommune uten kobling (legacy: `Ident.Empty` først): ugyldig for kommuner, se A2.
 * - Veg og adresseparsell (legacy: første del beholder ID-en): første del har `role: identity`, som gir
 *   `Move` på første del og `Derive` for de andre. Etterkommere uten egen regel følger da med til
 *   identitetsdelen, se A8.
 * - Legacy `Mapping.Split` der en del er kilden selv, se A7.
 *
 * **A7. Utskilling.** Legacy modellerte «kommune 1234 fortsetter, og 1222 er ny» som `Split` til seg selv og
 * den nye. I reform kan ikke en del i `split` være kilden selv (`SourceIsTarget`). Det skrives som `create`
 * med `derivedFrom` (en `Derive` uten regel for kilden, så kilden står i ro). Den nye kommunen har
 * kommunepayload (`WrongPayload` i domenereglene). Formatet godtar utskilling, men `toReguleringDTO` avviser den
 * (`bro`). Transformeren støtter `Derive`, så det testes her.
 *
 * **A8. Delvis nøkkel.** Både legacy og reform gir delvis nøkkel (bare komponentene under treffet) til en entitet
 * uten egen regel som ligger under en kilde som utgår (`Retire`). Det er et bevisst signal til sinkene om at
 * entitetens egen nøkkel ikke avgjør ny kommune. Sinkene i matrikkel-komreg (`no.statkart.matrikkel.komreg.mottaker`)
 * bruker det slik:
 * - Bygning og Sefrak finner kommunen via de assosierte identene. Går ikke det, logges en advarsel og entiteten
 *   hoppes over, så den kan rettes etterpå uten at hele kjøringen feiler.
 * - Vegadresse og matrikkelenhet støtter ikke delvis nøkkel (`getOrThrow`). Der feiler hele sinken.
 *
 * Reform beholder oppførselen der den er gyldig: en bygning under en kommune som deles (`role: successor`), får
 * samme delvise nøkkel som i legacy.
 *
 * Avviket gjelder veg som deles. Legacy lot første del beholde ID-en, men ga likevel vegadressene under delvis
 * nøkkel, og da feilet vegadresse-sinken. I reform skrives delingen med `role: identity` (`Move`), og vegadresser
 * uten egen regel følger identitetsdelen. Legacy-testen «vegadressen transformeres tvetydig» er ikke portert. I
 * reform krever den en veg som deles uten `role`, og det avviser domenereglene (`ActionNotSupported`, vegen ville
 * blitt slettet). Testen «vegadressen følger med til identitetsdelen» dokumenterer den nye oppførselen.
 *
 * **A9. Payload bare der domenereglene krever den.** Nye kommuner har kommunepayload med kommunevåpen
 * (`WrongPayload`, `MissingMunicipalityCoat`), også i bygning- og vegtestene, der legacy ikke sjekket payload.
 * Nye veger har ingen payload. `resultObject` sjekkes bare der legacy sjekket det.
 *
 * **A10. Bygning er ikke en kind.** Formatet har ingen `kind` for bygning, så reglene i bygningstestene
 * gjelder kommuner og gårdsnumre. Bygningen er bare en entitet som blir påvirket via kommunenøkkelen, som i
 * legacy-testene. Legacy-reglene `Mapping.Replace` på gårdsnummer blir `move` av `landNumber`.
 *
 * **A11. Navn og pakke.** Testene ligger i pakken `no.kartverket.komreg.transformation.reform`, og
 * `LegacyTransformEntityTest` heter `ReformClaimsTransformerEntityTest`. Legacy-testene brukte mockk for
 * `IdGeneratorManager`. Her brukes en enkel `idFor`-lambda, siden bare identtypen og ID-verdien er relevant.
 *
 * Testene krever `testImplementation(project(":regulering-api:regulering-api-reform"))` i `lib/transformation`.
 * Modulen har `lib:transformation` som `api`-avhengighet, men syklusen går bare mellom test og main, så Gradle
 * godtar den.
 */
internal val IKRAFTTREDELSE = LocalDate(2027, 1, 1)

private val registry: SegmentRegistry by lazy { runBlocking { SegmentRegistry.v1(NumberingScheme.Production) } }

private val vapen: ByteArray = createDummyKommunevaapenImage('K')

/** Bygger [ReformClaims] fra noder i `operasjoner`. Feiler med alle feilene hvis spec-en ikke er gyldig. */
internal fun claimsFromSpec(vararg operasjoner: String): ReformClaims {
    val json =
        """{ "navn": "Test", "ikrafttredelse": "$IKRAFTTREDELSE", "operasjoner": [ ${operasjoner.joinToString()} ] }"""
    val resolved = Json.decodeFromString(ReformSpec.serializer(), json)
        .resolve(registry)
        .getOrElse { errors -> error("Spec kunne ikke tolkes:\n${errors.joinToString("\n")}") }
    val claims = ReformClaims.validate(resolved.ops)
        .getOrElse { errors -> error("Spec ga ugyldige regler:\n${errors.joinToString("\n") { it.message }}") }
    claims.validateDomain(registry)
        .onLeft { errors -> error("Spec bryter domenereglene:\n${errors.joinToString("\n") { "${it.rule}: ${it.message}" }}") }
    return claims
}

internal fun transformerFromSpec(vararg operasjoner: String) = ReformClaimsTransformer(claimsFromSpec(*operasjoner))

/** `idFor` som alltid gir samme ID. For tester der ID-verdien for nye entiteter ikke har betydning (A11). */
internal val dummyIdFor: suspend (IdType<*, *>, Any?) -> Id = { _, _ -> dummyId(1) }

/**
 * `payload` for en ny kommune med [navn], [kommunevapen] og ellers samme data som [dummyKommune].
 * Gir [forventetKommunedata] etter `resolve`.
 */
internal fun kommunePayload(navn: String, kommunevapen: ByteArray = vapen): String =
    """{ "navn": "$navn", "koordinatsystem": "UTM32", "senterpunkt": { "x": 123.0, "y": 456.0 },
         "godkjenteGardsnumre": [ { "fra": 1, "til": 1 }, { "fra": 2, "til": 2 }, { "fra": 3, "til": 3 } ],
         "kommunevapen": "${Base64.getEncoder().encodeToString(kommunevapen)}" }"""

/** `Kommunedata` som `kommunePayload(navn)` skal gi. Tilsvarer `Kommune.tilKommunedata` for [dummyKommune]. */
internal fun forventetKommunedata(navn: String): Kommunedata =
    dummyKommune(1, 1).tilKommunedata(IKRAFTTREDELSE).copy(navn = navn.uppercase())
