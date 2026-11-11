# Reform-format

Dette dokumentet beskriver JSON-formatet for reformer (reguleringer) slik frontend skriver det. En reform
er et tre av operasjoner. Formatet vet ikke hva en kommune eller en veg er, bare hvilke **kinds** (segmenter)
som finnes og hvordan de henger sammen. De samme fem operasjonene brukes for alle identtyper.

Modellen formatet oversettes til: [reform-op.md](reform-op.md).

## Toppnivå

```json
{
  "format": 1,
  "navn": "Reform 2027",
  "ikrafttredelse": "2027-01-01",
  "operasjoner": [ … ]
}
```

| felt | type | betydning |
|---|---|---|
| `format` | tall | Versjonen av formatet. Valgfri ved innsending, standard `1`. |
| `navn` | streng | Navnet på reformen. |
| `ikrafttredelse` | dato (`ÅÅÅÅ-MM-DD`) | Datoen reformen trer i kraft. Brukes også som ikrafttredelsesdato for nye kommuner. |
| `operasjoner` | liste | Operasjonene på rotnivå. |

Specen har ingen `id`. Identiteten er ressursen i regulering-api.

## Kinds

Hver kind har én fast forelder. Derfor avgjør `kind` hele stien fra roten. Stien til `addressNumber` er for
eksempel `county → municipality → addressCode → addressNumber`.

| kind | forelder | verdi i JSON | eksempel | tar payload |
|---|---|---|---|---|
| `county` | — | streng, 2 siffer (5 siffer godtas i test) | `"46"` | ja, se [Payload](#payload) |
| `municipality` | `county` | streng, 2 siffer (løpenummeret) | `"01"` | ja, se [Payload](#payload) |
| `addressCode` | `municipality` | tall, 1–99998 | `1234` | nei |
| `addressNumber` | `addressCode` | tall, 1–9998 | `5` | nei |
| `districtType` | `municipality` | streng, ikke tom | `"valg"` | nei |
| `districtNumber` | `districtType` | tall | `3` | nei |
| `landNumber` | `municipality` | tall, 0–99999 (gårdsnummer) | `40` | nei |
| `titleNumber` | `landNumber` | tall, 0–9999 (bruksnummer) | `1` | nei |
| `parcel` | `municipality` | tall (teig-ID) | `987654` | nei |

Strenger og tall skilles: `"46"` er et gyldig fylke, men `46` er det ikke, og `"1"` er ikke et gyldig
bruksnummer.

En `parcel` (teig) gjelder både «mnr mangler» (bnr 0) og «mnr vann mangler» (bnr 1). Én operasjon på en teig
gjelder begge.

## Nøkler

Det finnes to slags nøkler:

- **Pivot** (`key`, og elementene i `merge.from`) er **relativ** til konteksten, det vil si nøkkelen til
  noden over.
  - En skalar er ett segment av typen `kind`: `"key": 1234`.
  - En array er en sti av segmenter som slutter på `kind`: `"key": ["46", "01", 1234]`. Første ledd må være
    et barn av konteksten. På rotnivå betyr det at stien starter med fylket.
- **Mål** (`to`, `into.existing`, `into.new`) er alltid **absolutte** arrays fra roten, uansett hvor i treet
  de står. Målet har samme kind som kilden: `"to": ["46", "02", 1234]`.

```json
{ "op": "adjust", "kind": "municipality", "key": ["46", "01"], "then": [
  { "op": "move", "kind": "addressCode", "key": 1234, "to": ["46", "02", 1234] } ] }
```

Her er `1234` relativ til kommune 4601, mens målet er skrevet fullt ut.

## Operasjoner

Alle noder har `op` og `kind`. Barn ligger alltid i `then` og er relative til nøkkelen til noden.

| op | felter | betydning |
|---|---|---|
| `adjust` | `key`, `then`? | Ingen endring på `key`. Bare kontekst for barna. |
| `move` | `key`, `to`, `then`? | Samme entitet får ny nøkkel. Det som ligger under, og som ikke har egen regel, følger med. |
| `merge` | `from`, `into` | Kildene i `from` utgår og peker på målet i `into`. |
| `split` | `key`, `into`, `then`? | Kilden deles i partene i `into`. `"into": []` betyr at kilden oppløses. |
| `create` | `key`, `payload`, `then`? | Ny entitet uten kilde. |

### `merge`

```json
{ "op": "merge", "kind": "municipality",
  "from": [ ["46", "01"], { "key": ["46", "03"], "then": [ … ] } ],
  "into": { "existing": ["46", "02"], "then": [ … ] } }
```

- Elementene i `from` er en pivot, eller `{"key": pivot, "then": [...]}` når kilden har unntak.
- `into` er enten `{"existing": mål, "then"?: [...]}` (målet finnes fra før) eller
  `{"new": mål, "payload"?: {...}, "then"?: [...]}` (målet opprettes).
- Én kilde og `into.new` er en renummerering: målet får ny ID. Med to eller flere kilder og `into.new` er
  `payload` påkrevd.
- `then` under en kilde er relativ til kilden. `then` under `into` er relativ til målet.

### `split`

```json
{ "op": "split", "kind": "municipality", "key": ["46", "05"],
  "into": [ { "to": ["46", "10"], "role": "successor", "payload": { … } },
            { "to": ["46", "11"], "payload": { … } } ],
  "then": [ … ] }
```

En part er `{"to": mål, "payload"?: {...}, "role"?: "identity" | "successor"}`. Høyst én part har `role`:

- `identity`: denne parten arver kilden (samme ID). Den kan ikke ha payload. De andre partene er nye.
- `successor`: kilden utgår og peker på denne parten. Alle partene er nye.
- ingen `role`: kilden utgår uten peker. Alle partene er nye.

## Kontekster

Hvilke operasjoner som er lov i `then`, avhenger av hvor noden står:

| kontekst | oppstår i | lovlige op-er |
|---|---|---|
| står i ro | roten, `adjust.then`, `merge.into.existing.then` | alle |
| under en kilde | `move.then`, `split.then`, `merge.from[].then` | `move`, `split`, `merge` |
| under en ny nøkkel | `create.then`, `merge.into.new.then` | `create` |

Under en kilde følger alt uten egen regel kilden uansett, så `adjust` er ikke lov der.

### Mellomledd i en sti-pivot

Leddene foran det siste i en sti-pivot betyr det samme som et `adjust` der nøkkelen står i ro, og ellers
ingenting:

- På roten (og andre steder der nøkkelen står i ro) betyr `["50", "01", 12, 3]` at fylke 50, kommune 5001
  og gårdsnummer 5001-12 står i ro. Hvis kommune 5001 samtidig er kilde et annet sted, er det en feil
  (`KeptAndConsumed`).
- Under en kilde eller en ny nøkkel er mellomleddene bare navigasjon.

Unntak fra en kilde skrives derfor under kilden:

```json
{ "op": "merge", "kind": "municipality",
  "from": [ { "key": ["50", "01"], "then": [
    { "op": "move", "kind": "titleNumber", "key": [12, 3], "to": ["50", "55", 40, 1] } ] } ],
  "into": { "existing": ["50", "55"] } }
```

Kommune 5001 slås inn i 5055. Gårdsnummer 12 følger kommunen, mens bruksnummer 12/3 flyttes til 5055-40/1.
Det samme skrevet med `move` på roten og `merge` ved siden av gir `KeptAndConsumed`.

## Payload

`county`:

```json
{ "navn": "Buskerud" }
```

`municipality` (fylkes- og kommunenummer kommer fra nøkkelen, ikrafttredelsesdato fra `ikrafttredelse`):

```json
{
  "navn": "Nye Kommune",
  "koordinatsystem": "UTM32",
  "senterpunkt": { "x": 230000.0, "y": 6630000.0 },
  "nedsattKonsesjonsgrense": false,
  "godkjenteGardsnumre": [ { "fra": 1, "til": 200 } ],
  "adresse": { "adresselinje1": "Postboks 1", "adresselinje2": null, "postnummer": "3001" },
  "standardRekvirentOrgnummer": "123456789",
  "kommunevapen": "iVBORw0KGgo="
}
```

`navn`, `koordinatsystem` og `senterpunkt` er påkrevd. `kommunevapen` er base64. Navnet lagres med store
bokstaver. Ukjente felter er en feil.

## Eksempel

```json
{ "format": 1, "navn": "Reform 2027", "ikrafttredelse": "2027-01-01",
  "operasjoner": [
    { "op": "merge", "kind": "county",
      "from": ["03", { "key": "30", "then": [
        { "op": "merge", "kind": "municipality", "from": ["01", "02"],
          "into": { "new": ["33", "01"], "payload": { … } } },
        { "op": "split", "kind": "municipality", "key": "05",
          "into": [ { "to": ["33", "10"], "role": "successor", "payload": { … } },
                    { "to": ["33", "11"], "payload": { … } } ],
          "then": [
            { "op": "move", "kind": "addressCode", "key": 1234, "to": ["33", "11", 1234] },
            { "op": "split", "kind": "addressCode", "key": 1300,
              "into": [ { "to": ["33", "10", 1300], "role": "identity" }, { "to": ["33", "11", 1300] } ] } ] } ] }],
      "into": { "new": ["33"], "payload": { "navn": "Buskerud" } } },
    { "op": "adjust", "kind": "municipality", "key": ["46", "01"], "then": [
      { "op": "move", "kind": "landNumber", "key": 5, "to": ["46", "02", 7], "then": [
        { "op": "move", "kind": "titleNumber", "key": 1, "to": ["46", "02", 8, 1] } ] } ] }
  ] }
```

- Fylkene 03 og 30 slås sammen til det nye fylket 33.
- Under fylke 30 slås kommunene 3001 og 3002 sammen til den nye kommunen 3301, og 3005 deles i 3310
  (etterfølger) og 3311.
- Under kommune 3005 flyttes vegen 1234 til 3311. Vegen 1300 deles: den beholder ID-en i 3310, og får en kopi
  i 3311.
- Kommune 4601 står i ro. Gårdsnummer 5 flyttes til 4602-7, bortsett fra bruksnummer 1, som flyttes til
  4602-8/1.

Flere eksempler ligger i `regulering-api/reform/src/testFixtures/resources/reform-spec/valid/`.

## Feil

Alle feil samles og returneres samlet. Hver feil har en JSON-sti til stedet i specen, en stabil kode og en
norsk melding:

```json
{ "path": "$.operasjoner[0].from[1].then[2].key[1]", "code": "InvalidSegment",
  "message": "Ugyldig verdi \"2\" for municipality: Municipality serial must be a 2-digit string" }
```

Stien peker så nøyaktig som mulig: på segmentet (`key[1]`, `to[0]`), feltet (`payload`, `into`) eller noden.
Når en nøkkel ikke kan beregnes, sjekkes ikke nodene under den.

### Feilkoder fra formatet

| kode | sti | betyr |
|---|---|---|
| `UnknownKind` | `kind` | `kind` finnes ikke. |
| `OpNotAllowedInContext` | noden | Op-en er ikke lov i konteksten (se [Kontekster](#kontekster)). |
| `KindNotChildOfContext` | `key` eller `from[i]` | Pivoten har feil antall ledd, eller `kind` ligger ikke under konteksten. |
| `InvalidSegment` | segmentet | Verdien er ikke gyldig for kinden på den plassen (se [Kinds](#kinds)). |
| `TargetLengthMismatch` | målet | Målet har ikke like mange segmenter som stien fra roten til `kind`. |
| `MultipleRoles` | `into[i].role` | Mer enn én part i en `split` har `role`. |
| `PayloadOnIdentity` | `into[i].payload` | En part med `role: identity` har payload. |
| `EmptyMerge` | `from` | `merge` uten kilder. |
| `PayloadRequired` | `into` | `into.new` med to eller flere kilder mangler payload. |
| `PayloadNotAllowed` | `payload` | `kind` tar ikke payload. |
| `InvalidPayload` | `payload` | Payloaden kan ikke leses (manglende eller ukjente felter, feil type, ugyldig base64 og lignende). |
| `KeptAndConsumed` | stedet der nøkkelen står i ro | En nøkkel står i ro (`adjust`, mellomledd på roten eller `into.existing`), men er kilde i en annen node. Feilen har også `sourcePath`. |

En spec som ikke har riktig form (ukjent `op`, manglende felt, `null` som segment og lignende), avvises før
disse reglene sjekkes.

Formatet sjekker bare det som er synlig i specen. Konflikter mellom operasjoner (for eksempel to regler for
samme kilde, eller to operasjoner som oppretter samme mål) sjekkes av `ReformClaims.validate`, regler for hva
hver identtype støtter av [domeneregler](#domeneregler), og regler som trenger data fra matrikkelen i et
senere steg.

## Domeneregler

`ReformClaims.validateDomain(registry)` (pakken `no.kartverket.komreg.regulering.reform.rules`) avviser
operasjoner som er strukturelt gyldige, men som sinkene i matrikkel-komreg ikke støtter. Den bruker ingen IO,
samler alle feil, og gir for hver feil `ops`, en stabil `rule`-kode, en norsk melding og et `hint` om riktig
grep i formatet. Identtypen til en operasjon er kinden registeret finner for nøkkelen. Reglene ligger som en
tabell per kind i `SinkCapabilities.kt`.

| kind | opprette | avlede | flytte | slå sammen | utgå |
|---|---|---|---|---|---|
| `county` | ✓ | ✓ | ✗ ¹ | ✓ | ✓, uten etterfølger |
| `municipality` | ✓ | ✓ | ✗ ¹ | ✓ | ✓, etterfølger påkrevd ² |
| `addressCode` (veg) | ✗ | ✓, bare fra en kilde som flyttes ³ | ✓ | ✗ | ✗ ⁴ |
| `addressNumber`, `districtNumber`, `titleNumber` | ✗ | ✗ | ✓ | ✗ | ✗ |
| `landNumber` (gårdsnummer) | ✗ | ✗ | ✓ | ✗ | ✓, uten etterfølger og med flytting av bruksnumre under ⁵ |
| `parcel` (teig) | ✗ | ✗ | ✓, bare uten matrikkelnummer ⁶ | ✗ | ✗ |

Kinds som ikke står i tabellen (`districtType`) støttes ikke av noen sink (`UnsupportedIdentType`).

Tilleggskrav:

| regel | gjelder | krav |
|---|---|---|
| `WrongPayload` | `county`, `municipality` | Nytt fylke har `Fylkesdata`, ny kommune har `Kommunedata`. Gjelder både opprettelse og avledning. |
| `MissingMunicipalityCoat` | `municipality` | Ny kommune har kommunevåpen. |
| `RetireNeedsSuccessor` / `RetireHasSuccessor` | `municipality` / `county`, `landNumber` | Kommune som utgår har etterfølger. Fylke og gårdsnummer har ingen. |
| `SuccessorNotDerived` | `municipality` | Etterfølgeren er en av delene som er avledet fra samme kilde. |
| `TargetsInMultipleCounties` | `municipality`, `addressCode` | Alle mål fra én kilde ligger i samme fylke. |
| `DeriveWithoutMove` | `addressCode` | En veg deles med `role: identity`, så den har en `move`. |
| `SeriesRetireWithoutTitleMoves` | `landNumber` | Et gårdsnummer som utgår, har `move` av bruksnumre under seg. |
| `ParcelHasCadastralNumber` | `parcel` | Bare teiger uten matrikkelnummer (gnr 0, fnr 0, snr 0, bnr 0 eller 1) flyttes. |
| `ParcelNotPaired` | `parcel` | bnr 0 og bnr 1 flyttes parvis til samme mål. |
| `TargetTypeMismatch` | alle | Mål og etterfølger har samme kind som kilden. |
| `ActionNotSupported` | alle | Handlingen er lov for kinden (tabellen over). |

1. `FylkeSink` og `KommuneSink` tolker samme ID med ny nøkkel som at fylket eller kommunen utgår, uten at en ny
   rad opprettes. Riktig grep er `merge` med `into.new` (renummerering).
2. Kommunen har én peker til etterfølgeren (`nykommuneid`).
3. `VegTilbakeforingService` krever en kildeentitet.
4. En veg uten ny nøkkel **slettes**. Del vegen med `role: identity`.
5. Matrikkelenhet-sinken forutsetter ny nøkkel for hvert bruksnummer.
6. Teiger med matrikkelnummer er knyttet til matrikkelenheten, ikke bare til kommunen.

`server` har en tilsvarende sikring i broen til `Regulering` (`Umappbar`). Domenereglene fanger de samme
tilfellene tidligere og med bedre meldinger.

## Dataregler

`ReformClaims.validateData(kommuneService)` (pakken `...reform.rules`) sjekker mot data om fylker og kommuner.
`KommuneService` er SPI-et i core-api, og kalles blokkerende på `Dispatchers.IO`. Feil er `DataErr`, med samme
form som `DomainErr` (`ops`, `rule`, norsk melding og `hint`) og samles uten å avbryte ved første feil.
«Gyldig» betyr `gyldigTilDato == null`. Kommuner og fylker slettes aldri, de får bare `gyldigTilDato`.

| regel | krav |
|---|---|
| `MunicipalityNotCovered` | Et fylke som utgår (`RetireTo` eller `Retire`) har en egen kilderegel for hver gyldig kommune i fylket. `lookup` må treffe kommunenøkkelen eksakt, en regel for fylket dekker ikke kommunene. |
| `TargetNotFound` / `TargetExpired` | Fylke- og kommunenøkler i `requiredExistingTargets` finnes og er gyldige. |
| `SourceNotFound` / `SourceExpired` | Fylke- og kommunekilder (`SourceOp.source` og `Derive.from`) finnes og er gyldige. |

Avgrensninger:

- Andre identtyper (veger, gårdsnumre, teiger og så videre) sjekkes ikke ennå.
- Dekning av veger, adresser og matrikkelenheter under en kommune som deles, sjekkes ikke. Det hører til en
  tørrkjøring.
- Cachen i `server` laster ved oppstart og på nytt ved bom, så en kommune som utgår etter oppstart kan kreve
  dekning først etter neste bom eller omstart.

### Kommunecache i `regulering-api` (`server`)

`MatrikkelApiMunicipalityCache` implementerer `KommuneService` og `MatrikkelApiMunicipalityNumbers`. Den laster
fylker, kommuner, `Postnummeromrade` og `JuridiskPerson` ved oppstart (via `MunicipalityDataSource`), og laster
alt på nytt én gang ved bom (ukjent `MunicipalityId` eller kommunenummer). Feltene tolkes som i `KommuneServiceImpl`
i matrikkel-komreg (`MunicipalityMapping.kt`).

Kommunevåpen hentes første gang en kommune trenger det og beholdes deretter, fordi bare noen få kommuner er med
i en reform. Tiden for å laste alle ved oppstart er ikke målt. `findAlleKommuner()` gir kommuner med våpen og
laster derfor alle som mangler. Datareglene trenger ikke våpen og bør bruke `withoutCoatOfArms`. Broen bruker
`findKommune(kommunenummer)` for enkeltkommuner.
