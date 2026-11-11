# ReformOp: regler for en regulering

`ReformOp` (i `lib/transformation`, pakken `transformation.reform`) er en liten regelmodell for hva en regulering
gjør med nøklene til fylker, kommuner, veger, adresser, matrikkelenheter og så videre. `ReformClaims` validerer
at reglene ikke motsier hverandre, og finner regelen som gjelder for en gitt nøkkel.

Modellen brukes slik:

```text
reform-format (JSON) ──resolve──▶ List<ReformOp> ──ReformClaims.validate──▶ ReformClaims ──toReguleringDTO──▶ Regulering (DTO)

Regulering (DTO) ──toReguleringsinput().toReformOps()──▶ List<ReformOp>
```

- `regulering-api` tar imot reformer i JSON-formatet i [reform-format.md](reform-format.md), oversetter dem til
  `ReformOp`, validerer dem og lager en `Regulering`-DTO med broen nedenfor.
- `ReformMapping.kt` går motsatt vei. Den lager regler av en eksisterende DTO.
- Ingen kode kjører `ReformOp` direkte. En regulering kjøres fortsatt av `server`, fra DTO-en:
  `Regulering` → `Reguleringsinput` → `mapInput` → `IdentTransformer` → sinkene i matrikkel-komreg. Se
  [arkitektur.md](arkitektur.md).

Operasjonene beskriver altså hva en regulering *betyr*. De er ikke instruksjoner som utføres.

## Nøkler og ID-er

En entitet har to slags identitet:

- **`Id`** er surrogatnøkkelen i matrikkelen. Den endres ikke når entiteten får nytt nummer.
- **`Ident`** er forretningsnøkkelen: en ordnet liste med komponenter fra toppen av hierarkiet og ned. Det er den
  reglene handler om.

I denne teksten skrives en `Ident` som en tuppel:

| Nøkkel | Betyr |
|---|---|
| `(46)` | fylke 46 |
| `(46, 01)` | kommune 4601 (fylke 46, løpenummer 01) |
| `(46, 01, 1234)` | veg med adressekode 1234 i kommune 4601 |
| `(46, 01, 12)` | gårdsnummer 12 i kommune 4601 |
| `(46, 01, 12, 3)` | grunneiendom 12/3 i kommune 4601 |

En nøkkel har også en **identtype**, altså typen til hver komponent. `(46, 01, 12)` som gårdsnummer og
`(46, 01, 12)` som adressekode er to ulike nøkler.

Nøklene danner et tre. `(46, 01)` er forelder til `(46, 01, 12)`, som er forelder til `(46, 01, 12, 3)`. En regel
for en nøkkel gjelder også for alle etterkommere som ikke har en egen, mer spesifikk regel. Se
[Etterkommere uten egen regel](#etterkommere-uten-egen-regel).

## Operasjonene

| Operasjon | Betydning |
|---|---|
| `Create(target, payload)` | En ny entitet uten kilde. All data ligger i `payload`, som er påkrevd. |
| `Derive(target, from, payload?)` | En ny entitet (ny `Id`) avledet fra entiteten med nøkkelen `from`. Sier ingenting om hva som skjer med `from`. |
| `Move(source, target)` | Samme entitet, med samme `Id`, får ny nøkkel. Etterkommere uten egen regel følger med. |
| `Retire(source, successor?)` | Kilden utgår. `successor` er en valgfri peker fra kilden til én av delene den er delt i. |
| `RetireTo(source, target)` | Kilden utgår og peker på `target`. Etterkommere uten egen regel følger med til `target`. Flere kilder kan peke på samme mål. |

`Move`, `Retire` og `RetireTo` er **kilderegler** (`SourceOp`). De bestemmer hva som skjer med en nøkkel som finnes
før reguleringen, og hver kilde kan ha høyst én. `Create` og `Derive` er **nye entiteter** (`NewEntity`). Bare de
gir nye `Id`-er.

`Move` og `RetireTo` kan se like ut, men betyr forskjellige ting:

- `Move((46, 01, 1234), (46, 50, 1234))`: vegen *er* den samme vegen etterpå, bare med ny nøkkel.
- `RetireTo((46, 01), (46, 50))`: kommune 4601 utgår, og kommune 4650 er en annen entitet. Den utgåtte kommunen
  peker på den nye.

### Eksklusive og svake mål

Hver operasjon kan ha et mål, og målet er enten eksklusivt eller svakt:

- **Eksklusivt mål** (`Create.target`, `Derive.target`, `Move.target`): operasjonen eier nøkkelen alene. To regler
  kan ikke ha samme eksklusive mål.
- **Svakt mål** (`RetireTo.target`, `Retire.successor`): bare en peker. Flere regler kan peke på samme mål, og
  pekeren oppretter ikke målet. Målet må enten opprettes eller flyttes hit av en annen regel, eller finnes fra
  før.

Eksempel: to kommuner slås sammen til en ny. Begge kildene peker svakt på den nye kommunen, og én `Create` eier den:

```kotlin
reform {
    retireTo(k4601, k4650)
    retireTo(k4602, k4650)
    create(k4650, kommunedata4650)
}
```

## Vanlige reguleringer

| Regulering | Regler |
|---|---|
| Vegen får ny adressekode, eller flyttes til en annen kommune | `Move(gammelVeg, nyVeg)` |
| Kommune 4601 får nytt nummer, 4650 | `RetireTo(4601, 4650)` + `Derive(4650, from = 4601, payload)` |
| 4601 og 4602 slås sammen til en ny kommune, 4650 | `RetireTo(4601, 4650)` + `RetireTo(4602, 4650)` + `Create(4650, payload)` |
| 4602 slås inn i en kommune som finnes, 4601 | `RetireTo(4602, 4601)` |
| 4601 deles i 4650 og 4651, og 4650 er etterfølgeren | `Retire(4601, successor = 4650)` + `Derive(4650, from = 4601)` + `Derive(4651, from = 4601)` |
| Fylke 46 deles i 47 og 48 | `Retire(46)` + `Derive(47, from = 46)` + `Derive(48, from = 46)` |
| Gårdsnummer 12 utgår, og bruksnumrene flyttes | `Retire((46, 01, 12))` + én `Move` per bruksnummer, for eksempel `Move((46, 01, 12, 3), (46, 01, 40, 1))` |

Et nytt kommunenummer gir en ny kommune i matrikkelen. Derfor er det ikke en `Move`, men `RetireTo` og `Derive`. Det
samme gjelder fylker.

`Derive` brukes når den nye entiteten har én kilde. Den blir da en del av kildens historikk. `Create` brukes når
målet har flere kilder, eller ingen.

En kommunedeling har alltid en etterfølger. Den gamle kommunen og referanser til den peker på etterfølgeren. En
fylkesdeling har ingen etterfølger.

## Etterkommere uten egen regel

`ReformClaims.lookup(key)` finner regelen for en nøkkel. Først prøves hele nøkkelen, så forelderen, og så videre opp
til roten. Den første kilderegelen som treffes, gjelder:

```text
regler:  RetireTo((46, 01), (46, 50))
         Move((46, 01, 1234), (46, 50, 5678))

lookup((46, 01, 1234))     → Move           matchedSize = 3, unmatchedSize = 0 (eksakt treff)
lookup((46, 01, 1234, 7))  → Move           matchedSize = 3, unmatchedSize = 1
lookup((46, 01, 999))      → RetireTo       matchedSize = 2, unmatchedSize = 1
lookup((46, 02, 999))      → null           ingen regel
```

`matchedSize` er antall komponenter i nøkkelen regelen gjelder for. `unmatchedSize` er antall komponenter under den.

Hva som skjer med en etterkommer som treffes av en forelders regel, avhenger av regelen:

- **`Move` og `RetireTo`**: etterkommeren følger med. Komponentene som ble truffet, byttes ut med målets, og resten
  beholdes. Veg `(46, 01, 999)` blir `(46, 50, 999)` i eksempelet over.
- **`Retire`**: det finnes ikke ett sted etterkommeren kan følge med til. Den får en *delvis nøkkel*, med bare
  komponentene under kilden. Eksempel: `Retire((46, 01, 12))` gjelder for grunneiendom 12/3, `(46, 01, 12, 3)`, når
  den ikke har en egen regel. Grunneiendommen får nøkkelen `(3)`, altså bare bruksnummeret. Hva det betyr, avgjøres
  av sinkene. `IdentTransformer` gjør det samme.

Alt under en kilde som utgår med `Retire`, bør derfor ha en egen regel. Ingen kode sjekker dette, fordi det krever
at man vet hva som finnes under kilden i matrikkelen.

## Validering

`ReformClaims.validate(ops)` sjekker at reglene henger sammen før de brukes. Identiske regler regnes som én. Alle
feil samles i en `NonEmptyList<ReformErr>`. Hvis det ikke er noen feil, returneres et `ReformClaims`.

| Feil | Regel | Eksempel på brudd |
|---|---|---|
| `Diverges` | En kilde har høyst én kilderegel. | `Move(a, b)` og `RetireTo(a, c)` |
| `Conflict` | Et eksklusivt mål har bare én eier. | `Move(a, c)` og `Move(b, c)` |
| `SourceIsTarget` | En nøkkel kan ikke være både kilde og mål, heller ikke svakt mål. | `Move(a, b)` og `Move(b, c)`, en kjede |
| `TargetUnderSource` | Et mål kan ikke ligge under en kilde, for da ville målet selv blitt flyttet eller ha utgått. | `RetireTo((46, 01), (46, 50))` og `Move(x, (46, 01, 1234))` |
| `SourceUnderCreatedTarget` | En kilde, eller `from` i en `Derive`, kan ikke ligge under et mål som opprettes. Den finnes ikke før reguleringen, så regelen ville aldri treffe noe. | `Create((46, 50), …)` og `Move((46, 50, 1234), x)` |
| `DeriveFromTarget` | `Derive.from` kan ikke være et eksklusivt mål. Det finnes ikke før reguleringen. | `Create(b, …)` og `Derive(c, from = b)` |
| `DeriveTypeMismatch` | `Derive.from` og `Derive.target` har samme identtype. | En kommune avledet fra et fylke |

Flere `Derive` fra samme kilde er lov. Det er slik en deling uttrykkes.

Et validert `ReformClaims` har også:

- `lookup(key)`, se over.
- `requiredExistingTargets`: svake mål som ingen regel oppretter eller flytter hit. De må finnes fra før. I
  eksempelet «4602 slås inn i 4601» er dette `{4601}`. Settet regnes ut fra reglene alene, uten oppslag i
  matrikkelen.
- `creates`: alle `Create`.
- `derives`: alle `Derive`, gruppert på `from`.

`validate` sjekker ikke om kildene finnes, om målene i `requiredExistingTargets` finnes, hvilke `Id`-er som tildeles,
eller om sinkene kan utføre reglene. I `regulering-api` sjekker datareglene at kilder og mål finnes (bare for fylker
og kommuner), og domenereglene sjekker hva sinkene kan utføre. Se [reform-format.md](reform-format.md#validering).

## Fra DTO til regler

`Reguleringsinput.toReformOps()` (i `ReformMapping.kt`) lager regler av en eksisterende regulering:

- **Fylke- og kommuneendring** med ett mål gir `RetireTo`. Med flere mål er det en deling, og den gir `Retire`.
  For kommuner er første mål etterfølger, og fylker har ingen etterfølger.
- **Mål for fylke eller kommune** opprettes hvis minst én endring til det ikke har `sammenslaaing = true`. Ellers må
  målet finnes fra før. Et mål som opprettes fra én endring, blir en `Derive` fra kilden. Et mål som opprettes fra
  flere endringer, blir én `Create`.
- **Veg**: én til én gir `Move`. Ved deling flyttes vegen til første mål, og de andre målene avledes fra den.
- **Vegadresse og krets** gir `Move`.
- **Teig** gir to `Move`, én for bruksnummer 0 («matrikkelnummer mangler») og én for bruksnummer 1
  («matrikkelnummer vann mangler»).
- **Matrikkelenhet**: gårdsnummerserien får `Move` hvis den får nytt gårdsnummer, `Retire` hvis den utgår, og ingen
  regel hvis den beholder nummeret. Hvert bruksnummer som er oppgitt, får en egen `Move`.

Gårdsnummerserier og veger som er mål, antas å finnes. DTO-en kan ikke opprette dem.

## Fra regler til DTO: broen

`toReguleringDTO` (i `lib/regulering-mapping`) lager en `Regulering` av validerte regler:

```kotlin
val claims = ReformClaims.validate(reform {
    move(gammelVeg, nyVeg)
}).getOrElse { feil -> error("Ugyldige regler: $feil") }

val regulering = claims.toReguleringDTO(id, navn, dato, kommuneService)
    .getOrElse { feil -> error("Kan ikke uttrykkes som regulering: $feil") }
```

`gammelVeg` og `nyVeg` er vegnøkler med samme identtype. `kommuneService` brukes til å slå opp kilder og mål som
finnes fra før.

Broen legger alle reglene i én `EndringDTO`. Deretter kontrollerer den at `regulering.toReguleringsinput().toReformOps()`
gir den samme mengden regler, med like payloads, men uten krav til rekkefølge. Hvis ikke, returneres
`UlikTransformasjon`. Denne rundturssjekken gjør `toEndringDTO` ikke alene, så bruk `toReguleringDTO` når du trenger
garantien.

### Hva broen ikke kan uttrykke

DTO-en kan uttrykke mindre enn reglene. Broen returnerer `IngenDtoEkvivalent` med en forklaring for blant annet:

- `Move` av et fylke eller en kommune. En endring i DTO-en lager alltid en ny entitet.
- Kommunedeling der etterfølgeren ikke opprettes av delingen, for eksempel en del som slås inn i en eksisterende
  kommune. DTO-en oppretter alle målene ved deling.
- Utskilling, altså en `Derive` fra en kilde som ikke har en kilderegel og dermed fortsetter.
- Deling med færre enn to mål, fylkesdeling med etterfølger og kommunedeling uten etterfølger.
- Kommune- eller vegdeling med mål i flere fylker.
- En gårdsnummerserie med en annen regel enn `Move`, bortsett fra `Retire` uten etterfølger når minst ett
  bruksnummer i serien flyttes.
- `Create` av et mål som ingen fylke- eller kommuneendring peker på.

Andre feil er `FinnesIkke`, for et mål som verken opprettes eller finnes i `kommuneService`, og `UgyldigPayload`,
for eksempel når kommunevåpenet mangler.

### Normalisering

Broen tar bare med det transformasjonen leser:

- En regulering har én ikrafttredelsesdato. Nye kommuner med en annen dato får reguleringens dato, og avviket logges
  som en advarsel.
- Navn på nye kommuner skrives med store bokstaver.
- Felt som ikke finnes i `Kommunedata`, som poststed, navn på standardrekvirent og `gyldigTilDato`, blir tomme.
- Rekkefølgen på reglene beholdes ikke.

Broen er derfor ikke et tapsfritt eksportformat.

### Samme regler betyr ikke samme kjøring

En vellykket rundtur betyr at DTO-en *beskriver* de samme reglene. Den betyr ikke at `mapInput` og `IdentTransformer`
utfører dem slik regelmodellen beskriver.

Eksempel: kommune 4601 deles i 4650 og 4651. I regelmodellen utgår 4601, og 4650 og 4651 er nye entiteter med nye
`Id`-er. `IdentTransformer` lager i stedet en transformasjon der den eksisterende kommunen *beholder* sin `Id` og får
nøkkelen til 4650, i tillegg til nye entiteter for 4650 og 4651.

Tilsvarende forskjeller kan finnes for etterfølgere, sammenslåingsflagget, hvilke etterkommere som følger med hvor,
og teiger.
