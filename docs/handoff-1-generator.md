# Handoff 1: `ReformTransformer`

**Anbefalt modell:** sterkeste resonneringsmodell (Claude Opus). **Reasoning:** høy.

Lag en ny generator i `lib/transformation` som lager `Transformation`-er direkte fra `ReformClaims`. Den skal
erstatte `IdentTransformer` (`IdentTransformer.kt`) og `mapInput` (`Transformer.kt`). Ingen `Mapping`-typer skal
brukes. Ikke koble den inn i `transform()` eller `server` her. Det gjøres i
[handoff-3](handoff-3-kjoring-fra-regler.md).

Les først: [reform-op.md](reform-op.md), `ReformOp.kt`, `ReformClaims.kt`, `IdentTransformer.kt` (spesielt
`transform()` linje ~39–113), `Transformation`/`Entity` i `lib/core-api/.../spi/EntitySource.kt` og sinkene i
`/home/larale/Projects/komreg/matrikkel/komreg/src/main/kotlin/no/statkart/matrikkel/komreg/mottaker/`.

## Grensesnitt

Forslag. Navn og plassering kan justeres.

```kotlin
// lib/transformation/.../reform/ReformTransformer.kt
class ReformTransformer(
    private val claims: ReformClaims,
    private val idTypeFor: suspend (IdentOrEmptyType<*>) -> IdType<*, *>?,
) {
    /** Transformasjonene for én eksisterende entitet. Tom liste hvis ingenting endres. */
    suspend fun transform(entity: Entity, idFor: suspend (IdType<*, *>, Any?) -> Id): List<Transformation>

    /** Én transformasjon per `Create`. Kalles én gang, etter alle `EntitySource`-er. */
    suspend fun creates(idFor: suspend (IdType<*, *>, Any?) -> Id): List<Transformation>
}
```

`idFor` er `IdGeneratorManager::idFor`. Bruk alltid mål-identen som `hint`, slik `IdentTransformer` gjør
(`idProvider(entity.id.type, transform.first)`). Da blir ID-ene stabile per mål.

## Semantikk

### Hovedident

`m = claims.lookup(entity.ident)` gir regelen for hovedidenten. Har entiteten ingen hovedident (`ident == null`,
for eksempel teiger fra `TeigSource`), er `transformedIdent = null`, og bare de assosierte identene regnes ut.

| Regel (`m.operation`) | Treff | `transformedIdent` | `id` |
|---|---|---|---|
| ingen | – | uendret | `entity.id` |
| `Move(s, t)` / `RetireTo(s, t)` | eksakt | `t` | `entity.id` |
| `Move(s, t)` / `RetireTo(s, t)` | forelder | `t` + komponentene under `s` | `entity.id` |
| `Retire(s, succ)` | eksakt | `succ`, eller `null` uten etterfølger | `entity.id` |
| `Retire(s, _)` | forelder | delvis nøkkel: bare komponentene under `s` | `entity.id` |

"`t` + komponentene under `s`" betyr: bytt ut de `m.matchedSize` første komponentene med `t`, og behold resten.
Mål-identen må få riktig identtype, altså samme type som `entity.ident` når `t` og `s` har samme type. Bygg den via
cachen (`append`, `identTypeOfN`, `makeOrGetFromCache`), ikke med en ny `IdentType`. Se om `Ident`/`IdentType`
allerede har hjelpefunksjoner for å bytte prefiks før du skriver nye. `IdentTransformer.transformIdent()` har
logikk for delvise nøkler som kan gjenbrukes.

`null` betyr "slett" for `VegTilbakeforingService`. For `FylkeSink` og `KommuneSink` gir både `null` og
`Ident.Empty` `nykommuneid = null`. Dagens fylkedeling sender `Ident.Empty`. Bekreft i sinkene at `null` er greit
der, ellers bruk `Ident.Empty` for fylke og kommune.

### Assosierte identer

Hver ident i `entity.associatedIdents` regnes ut med samme tabell, uten `Derive`. En assosiert ident som får `null`
(eksakt `Retire` uten etterfølger), tas ut av settet. Dette er beslutning 1 i
[oversikten](handoff-reform-transformasjon.md#beslutninger-som-er-tatt).

### Ingen endring

Er hovedident og assosierte identer uendret, og finnes ingen `Derive` fra hovedidenten, returneres en tom liste.
Det er det samme som dagens `null`.

### `Derive`

For hver `d` i `claims.derives[entity.ident]` (bare eksakt treff på `from`) lages én ekstra transformasjon:

```kotlin
Transformation(
    id = idFor(entity.id.type, d.target),
    sourceEntity = entity,
    transformedIdent = d.target,
    transformedAssociatedIdents = /* samme som for kilden */,
    resultObject = d.payload,
    sammenslaaing = null,
)
```

`id != sourceEntity.id`, så `transform()` sender den i første pass (nye entiteter). `VegTilbakeforingService`
lager da en kopi av vegen fra `sourceEntity`. `KommuneSink`/`FylkeSink` bruker bare `transformedIdent` og
`resultObject`.

**Utskilling** er en `Derive` fra en kilde uten kilderegel. Kilden er uendret, så den får ingen egen
transformasjon. Det kommer bare én transformasjon, for den nye entiteten.

### `Create`

`creates()` lager én transformasjon per `claims.creates`:

```kotlin
val idType = idTypeFor(c.target.type) ?: error("Ingen IdType for ${c.target.type}. Mangler IdTypeProvider?")
Transformation(
    id = idFor(idType, c.target),
    sourceEntity = null,
    transformedIdent = c.target,
    resultObject = c.payload,
    sammenslaaing = null,
)
```

Kall `creates()` én gang, ikke per kilde. Dagens løsning lager én opprettelse per kilde som peker på målet.

### `sammenslaaing`

Ingen sink leser feltet. I dag styrer det bare om `IdentTransformer` lager en ny entitet. Sett det til `null`.

## Avvik fra dagens kjøring

Dette er ment. Dokumenter dem i testene.

| Område | I dag (`IdentTransformer`) | Nå |
|---|---|---|
| Fylkeregler og kommunevise entiteter | Reglene samles per kommune (`kommuneviseRegler`), så en fylkeregel når aldri en kommunevis entitet. `MunicipalityNotCovered` kompenserer. | `lookup` går opp hele forelderkjeden. En fylkeregel gjelder også for kommuner uten egen regel. Dataregelen `MunicipalityNotCovered` hindrer fortsatt at det skjer i praksis. |
| Flere regler treffer | Kaster "More than one match" mens kjøringen pågår. | `ReformClaims.validate` avviser det før kjøringen (`Diverges`). |
| Gårdsnummerserie | `Split(HashSet)`. Hvilken serie som beholder ID-en er tilfeldig. Bruksnumre uten egen regel får delvis nøkkel, og `MatrikkelenhetTilbakeforingService` krasjer på `getKommunenummerOrThrow()`. | `Move(serie, ny)`. Bruksnumre uten egen regel følger med til `ny`. |
| Vegdeling med samme kommune to ganger | `mapVegendring` bruker `indexOf` og kan velge feil adressekode. | Hver `Derive` har sitt eget mål. |
| Vegdeling på tvers av fylker | `fylkesnummer.til.single()` stopper den. | Støttes. Sinken har ingen fylkesjekk. |
| Utskilling | Kan ikke uttrykkes. | `Derive` uten kilderegel. Bare den nye entiteten får en transformasjon. |
| Sammenslåing til ny kommune | Én opprettelse per kilde, samme ID via `idFor`-cachen. | Én `Create`. |
| Kommunedeling | Etterfølgeren kommer to ganger: kilden får etterfølgerens nøkkel, og etterfølgeren opprettes i tillegg. | Kilden får `transformedIdent = successor`, og hver del opprettes én gang via `Derive`. Sjekk at `KommuneSink` gir samme resultat. |
| Assosierte identer ved deling | `flatMap` over alle delene. | Følger bare kilderegelen, så én ident per assosiert ident. |
| `sammenslaaing` | Satt fra DTO-en. | `null`. |
| Deling uten etterfølger | Fylkedeling gir `transformedIdent = Ident.Empty` for kilden (og `Ident.Empty` blant de assosierte identene). | `null`, og en assosiert ident uten etterfølger fjernes. `FylkeSink` og `KommuneSink` bruker `get…OrNull()` og oppfører seg likt. |
| Vegdeling, vegadresser uten egen regel | Treff på forelder i en `Split` gir delvis nøkkel (bare adressenummeret). | `toReformOps` gir `Move(kilde, første mål)`, så adressene følger det første målet. |
| Teig med matrikkelnummer | Regelen (F, L, TeigId) treffer teigen uansett matrikkelnummer. | Regelen har gnr 0 og bnr 0/1, så bare teiger uten matrikkelnummer flyttes. Dataregelen `ParcelHasCadastralNumber` hindrer at det skjer i praksis. |

`ReformTransformerSammenligningTest` kjører dagens og ny transformator på de samme DTO-ene og feiler hvis en
forskjell ikke er deklarert med en av radene over.

## Identtyper i bruk

Fra matrikkel-komreg `IdentTypes.kt`. Nøklene i `ReformOp` er prefikser av disse.

| Entitet | Komponenter | Typisk nøkkel i regel |
|---|---|---|
| Fylke | F | F |
| Kommune | F, L | F, L |
| Veg | F, L, Adressekode | F, L, Adressekode |
| Vegadresse | F, L, Adressekode, Nummer, Bokstav | F, L, Adressekode, Nummer. Bokstaven følger med. |
| Matrikkeladresse | F, L, G, B, Fnr | – (følger matrikkelenheten via forelder) |
| Matrikkelenhet | F, L, G, B, Fnr, Snr | F, L, G (serie) og F, L, G, B (grunneiendom) |
| Teig | F, L, G, B, Fnr, Snr, TeigId | bare assosiert, ingen hovedident |
| Bygning / Sefrak | F, L, nummer | F, L |
| Krets | F, L, Kretstype, Kretsnummer | F, L, Kretstype, Kretsnummer |

## Tester

Legg testene i `lib/transformation/src/test/.../reform/ReformTransformerTest.kt`. Bruk testhjelperne som finnes
(`MockSource`, `MockIdGenerator`, `TestIdentTypes`, `TestIdType`, `DummyKommune`).

1. **Én test per rad** i semantikktabellen, både med hovedident og med assosierte identer.
2. **Port tilfellene** i `IdentTransformerTest`, `IdentTransformerVegTest`, `IdentTransformerVegadresseTest`,
   `IdentTransformerBygningTest` og `Transformer*Test`. Bygg reglene med `reform { ... }` direkte, eller via
   `toReguleringsinput().toReformOps()` når testen har en DTO. Der svaret skal være annerledes, skal testen vise
   det nye svaret og vise til raden i [avvikstabellen](#avvik-fra-dagens-kjøring).
3. **Sammenligning med dagens kjøring.** For hver DTO i testene: kjør både `IdentTransformer(mapInput(input))` og
   `ReformTransformer(ReformClaims.validate(input.toReformOps()))` på de samme entitetene. Sammenlign
   `(id, transformedIdent, transformedAssociatedIdents, resultObject)` som mengder. Hver forskjell skal enten være
   tom eller listet i avvikstabellen. Testen skal feile ved en forskjell som ikke er forklart.
4. **Utskilling, vegdeling på tvers av fylker og gårdsnummerserie med bruksnumre uten egen regel.** Disse kan ikke
   lages via DTO-en, så bygg reglene direkte.
5. **`Create` uten `IdType`** gir en tydelig feil.

### Test av identtype-invarians

Beslutning 3: vi stoler på `IdentType`-cachen. Disse testene skal låse det, i `lib/core-api` eller
`lib/transformation`:

- `ident6.dropLast()` har samme `type`-instans som en nøkkel bygget med `identTypeOf4<F, L, G, B>()`, og
  `==`/`hashCode` stemmer.
- Den samme sammenligningen holder etter en JSON-rundtur av identen (`IdentTypeSerializer`).
- En nøkkel fra `dropLast` finner en regel i et `HashMap` med nøkler bygget på den andre måten. Dette er det
  `ReformClaims.lookup` gjør.

## Ferdig når

- `ReformTransformer` og testene over finnes, og `./gradlew :lib:transformation:test` går grønt.
- `IdentTransformer` og `mapInput` er ikke endret og brukes fortsatt av `transform()`.
