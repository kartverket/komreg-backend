# Handoff: transformasjoner direkte fra `ReformClaims`

Mål: `server` lager `Transformation`-er direkte fra validerte `ReformClaims`. `Regulering`-DTO-en,
`mapInput` og `IdentTransformer.Mapping` skal ut av kjøringen.

I dag går kjøringen slik (se [reform-op.md](reform-op.md#fra-regler-til-dto-broen)):

```text
ReformSpec ─▶ ReformClaims ─▶ Regulering (DTO) ─▶ Reguleringsinput ─▶ mapInput ─▶ IdentTransformer ─▶ Transformation
```

Målet er:

```text
ReformClaims ─▶ ReformTransformer ─▶ Transformation
```

Resultatet trenger ikke være identisk med dagens. Kjente svakheter i dagens løsning skal rettes, ikke
gjenskapes. Se [Avvik fra dagens kjøring](handoff-1-generator.md#avvik-fra-dagens-kjøring).

## Oppgaver

| # | Fil | Hva | Avhenger av | Anbefalt modell | Reasoning |
|---|---|---|---|---|---|
| 1 | [handoff-1-generator.md](handoff-1-generator.md) | `ReformTransformer` i `lib/transformation`: semantikk, tester og sammenligning med dagens kjøring | – | Sterkeste resonneringsmodell (Claude Opus) | Høy |
| 2 | [handoff-2-idtype-provider.md](handoff-2-idtype-provider.md) | `IdTypeProvider`-SPI i `core-api` og ny Gradle-modul som pakker inn matrikkel-komreg | – | Claude Sonnet | Medium |
| 3 | [handoff-3-kjoring-fra-regler.md](handoff-3-kjoring-fra-regler.md) | Koble generatoren inn i `transform()` og `server`, `StedsnavnService`, `ReguleringValidator` | 1, 2 | Claude Opus | Medium–høy |
| 4 | [handoff-4-opprydding.md](handoff-4-opprydding.md) | Fjerne DTO-speilende domeneregler, slette `IdentTransformer`/`mapInput`, oppdatere dokumentasjon | 3 | Claude Sonnet | Lav–medium |

1 og 2 kan gjøres parallelt. Generatoren tar en funksjon `(IdentOrEmptyType<*>) -> IdType<*, *>?` som parameter,
så den kan testes uten SPI-en.

### Hvorfor disse modellene

- **1** handler om semantikk. Den må stemme med kontraktene til sinkene i et annet repo
  (`matrikkel/komreg/.../mottaker`), og den har mange kanttilfeller: delvise nøkler, utskilling, teiger uten
  hovedident, og ID-er som skal være stabile. Feil her gir stille feil i matrikkelen. Bruk høy reasoning.
- **2** er mekanisk: en Gradle-modul, et SPI-grensesnitt og en ServiceLoader. Medium holder.
- **3** krysser flere moduler og har ett åpent valg om transport, se
  [handoff-3](handoff-3-kjoring-fra-regler.md#åpent-valg-transport). Medium–høy.
- **4** er sletting og dokumentasjon, med kompilator og tester som sikkerhetsnett.

## Beslutninger som er tatt

1. **Assosierte identer følger bare kilderegelen.** `transformedAssociatedIdents` regnes ut med
   `claims.lookup(ident)` per assosiert ident. `Derive` påvirker ikke assosierte identer. Ingen kilde i
   matrikkel-komreg legger rene fylke-, kommune- eller vegidenter i `associatedIdents`.
2. **`Create` får `IdType` fra en ny SPI, `IdTypeProvider`.** SPI-en er valgfri og bakoverkompatibel. Det finnes
   bare én plugin i dag. Implementasjonen ligger derfor i dette repoet, i en egen Gradle-modul som pakker inn
   matrikkel-komreg og som `server` er avhengig av. Den flyttes til matrikkel-komreg senere. Det blir ingen shim
   som lærer typer fra entiteter. En `Create` uten `IdType` feiler tydelig.
3. **Prefiks-oppslag stoler på `IdentType`-cachen.** `IdentType` sammenlignes på identitet, og alle instanser
   kommer fra `IdentType.makeOrGetFromCache`. `dropLast()` returnerer det cachede prefikset. Vi gir ikke
   `IdentType` strukturell `equals`. I stedet låser tester invariansen, se
   [handoff-1](handoff-1-generator.md#test-av-identtype-invarians).
