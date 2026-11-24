# Handoff 3: kjøring fra regler

**Anbefalt modell:** Claude Opus. **Reasoning:** medium–høy.

**Forutsetter** [handoff-1](handoff-1-generator.md) (`ReformTransformer`) og
[handoff-2](handoff-2-idtype-provider.md) (`IdTypeProvider`).

Koble `ReformTransformer` inn i kjøringen. Gjør det i to faser. Fase A bytter motor og beholder DTO-en som
transport. Fase B fjerner DTO-en fra transporten og krever et valg først.

## Fase A: ny motor, samme transport

### `transform()` (`lib/transformation/.../Transformer.kt`)

`transform()` bruker `input` bare til `IdentTransformer(mapInput(input))` og `input.ikrafttredelsesdato`. Bytt
parameteren:

```kotlin
suspend fun transform(
    kjoringId: Int,
    claims: ReformClaims,
    ikrafttredelsesdato: LocalDate,
    idTypeFor: suspend (IdentOrEmptyType<*>) -> IdType<*, *>?,
    lifeCycleHandlers: List<LifeCycleHandler>,
    // resten som før
)
```

I løkken over `entitySources`:

- `transformer.transform(entity, idGeneratorManager::idFor)` gir en liste. Bruk `flatMapConcat` / `transform`
  som i dag.
- **Etter** alle `EntitySource`-er og **før** `entityProcessors`: skriv `transformer.creates(idGeneratorManager::idFor)`
  til storage. Processorene leser alle transformasjoner fra storage. Derfor må opprettelsene ligge der før
  processorene kjører.
- Opprettelsene har `sourceEntity == null`. Filteret for første pass i sink-fasen
  (`sourceEntity == null || sourceEntity.id != it.id`) tar dem med som før.

Gjenopptatt kjøring (`erForstegangskjoring = false`) lager ingen nye transformasjoner, så den er ikke berørt.

### `server`

- `TransformationService.transformEntities` og `KjoringRoutes` (linje ~39–70): bygg reglene fra DTO-en:

  ```kotlin
  val input = regulering.toReguleringsinput()
  val claims = ReformClaims.validate(input.toReformOps())
      .getOrElse { error("Reguleringen ${input.id} gir ugyldige regler: $it") }
  transform(kjoringId, claims, input.ikrafttredelsesdato, idTypeProviderManager::idTypeFor, ...)
  ```

  `input.id` brukes til logging i `TransformationService`. Send den inn separat.
- Lag `IdTypeProviderManager` der de andre managerne lages, og send den inn.

### Overgang

Legg til et konfigurasjonsvalg for motor (`legacy` / `reform`), med `legacy` som standard. Fjern det i
[handoff-4](handoff-4-opprydding.md) når `reform` er verifisert på reelle reguleringer i et testmiljø. Kjør begge
motorene mot samme regulering, uten tilbakeføring (`skalTilbakefores = false`). Sammenlign transformasjonene i
storage, og forklar avvikene med [avvikstabellen](handoff-1-generator.md#avvik-fra-dagens-kjøring).

## Fase B: ingen DTO i transporten

### Åpent valg: transport

`server` og `regulering-api` er ikke avhengige av hverandre, og sending fra `regulering-api` til `server` er ikke
implementert (se [arkitektur.md](arkitektur.md)). `ReformOp` er ikke `@Serializable` i dag. **Avklar dette med
brukeren før fase B starter.** Alternativer:

1. **`regulering-api` sender validerte `ReformOp`-er.** Gjør `ReformOp` serialiserbar (`Ident` og `Payload` har
   serializere fra før). `server` lagrer reglene og kjører `ReformClaims.validate` på nytt. Domene- og
   datareglene kjøres i `regulering-api`. Dette er anbefalt: det er de løste, validerte reglene som kjøres, og
   `server` trenger verken `SegmentRegistry` eller `resolve`.
2. **`server` henter `ReformSpec` og kjører `resolve` selv.** Da må `server` være avhengig av
   `:regulering-api:regulering-api-reform`, `SegmentRegistry` og en `KommuneService`. Det gir to steder som
   kjører kjeden.
3. **Behold DTO-en som transport** og fjern den senere. Da kan ikke utskilling, vegdeling på tvers av fylker osv.
   sendes til `server`, selv om motoren støtter dem.

### `StedsnavnService` (`server/.../services/StedsnavnService.kt`)

Den leser `Reguleringsinput` (linje ~34–65). Lag samme utdata fra `ReformClaims` og datoen:

| Linje | I dag | Fra regler |
|---|---|---|
| `DT` | `input.ikrafttredelsesdato` | reguleringens dato |
| `NF` | `input.fylker` | `Fylkesdata`-payloads i `Create`/`Derive` for fylker |
| `NK` | `input.kommuner` | `Kommunedata`-payloads i `Create`/`Derive` for kommuner |
| `UK` | `Kommuneendring` fra → `til.first()` | kommune-`RetireTo(s, t)` gir `s → t`. `Retire(s, succ)` gir `s → succ`. |
| `UF` | `Fylkeendring.fra` | fylker med `Retire` eller `RetireTo` |

Sjekk i `toReguleringsinput` hva `fylker` og `kommuner` inneholder i dag, for eksempel om eksisterende mål for
sammenslåing er med. Lag en test som sammenligner utdataene fra gammel og ny vei for reguleringene i testene.

### `ReguleringValidator` (`server/.../validation/ReguleringValidator.kt`)

Den kjøres i `ReguleringService.getOrThrowRegulering` og validerer DTO-en. Reglene skal flyttes til domene- eller
datareglene i `regulering-api/reform/.../rules/` og kjøres der:

| DTO-regel | Ny domeneregel |
|---|---|
| `FYLKESDELING_KAN_IKKE_HA_SAMMENSLAAING`, `KOMMUNEDELING_KAN_IKKE_HA_SAMMENSLAAING` | Faller bort. Sammenslåingsflagget finnes ikke i `ReformOp`. |
| `FYLKESDELING_MANGLER_KOMMUNER` | Sjekk om dataregelen `MunicipalityNotCovered` (`DataRules.kt`) dekker regelen fra før. |
| `KOMMUNEDELING_MANGLER_KRETSER`, `_MATRIKKELENHETER`, `_TEIGER`, `_VEGER` | Krever at man vet hva som finnes under kilden. Avklar om regelen skal være en dataregel (oppslag i matrikkelen) eller fjernes. |
| `VEGDELING_MANGLER_VEGADRESSER` | En `Derive` av en veg uten noen vegadresse-`Move` til målet. |

Når `server` ikke lenger tar imot DTO-er, kan `ReguleringValidator` slettes.

## Ferdig når

- **Fase A:** `server` kjører med begge motorene, og `./gradlew build` går grønt. Det finnes en test som kjører
  `transform()` med `ReformClaims` mot `MockSource`/`MockSink`, med minst én `Create`.
- **Fase B:** `server` kjører fra regler uten `Regulering`-DTO. `StedsnavnService` og valideringen er flyttet.
