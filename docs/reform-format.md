# Reform-format

En **reform** (regulering) beskriver hvordan nøkler i matrikkelen endres når fylker og kommuner slås sammen,
deles eller får nye nummer. Den sier for eksempel hvilke kommuner som slås sammen, og hvilke veger og
gårdsnumre som flyttes til en annen kommune.

Frontend skriver en reform som en **spec**: et JSON-dokument med et tre av operasjoner. `regulering-api`
validerer og lagrer specen, og oversetter den til `Regulering`-DTO-en som `server` kjører. Dette dokumentet
beskriver formatet, valideringen og endepunktene.

- Hvorfor formatet er bygd slik, og hvordan valideringen er implementert: [reform-format-design.md](reform-format-design.md).
- Regelmodellen specen oversettes til (`ReformOp`): [reform-op.md](reform-op.md).

## Begreper

| begrep | betydning |
|---|---|
| nøkkel | Forretningsnøkkelen til en entitet, for eksempel kommune 4601, eller veg 1234 i kommune 4601. En nøkkel er en sti av segmenter fra roten: `["46", "01", 1234]`. Kalles `Ident` i backend. |
| ID | Den interne ID-en til entiteten i matrikkelen. En entitet kan få ny nøkkel og beholde ID-en. |
| kind | Typen til et segment, for eksempel `county` (fylke) eller `addressCode` (veg). |
| node | Et objekt i `operasjoner` eller i en `then`-liste. |
| kontekst | Nøkkelen til noden over. På rotnivå er konteksten tom. |
| pivot | Nøkkelen en node gjelder (`key` og elementene i `merge.from`). Skrives relativt til konteksten. |
| mål | En nøkkel noe flyttes til, slås sammen inn i, deles til eller avledes fra. Skrives alltid fullt ut fra roten. |
| payload | Data til en ny entitet, for eksempel navn og senterpunkt for en ny kommune. |
| står i ro | Nøkkelen endres ikke av reformen. |

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
| `format` | tall | Versjonen av formatet. Valgfri, standard `1`. |
| `navn` | streng | Navnet på reformen. |
| `ikrafttredelse` | dato (`ÅÅÅÅ-MM-DD`) | Datoen reformen trer i kraft. Gjelder også som ikrafttredelsesdato for alle nye kommuner. |
| `operasjoner` | liste | Nodene på rotnivå. |

Specen har ingen `id`. ID-en tildeles av `regulering-api` når specen lagres.

## Kinds

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

Hver kind har én fast forelder, så kinden avgjør hele stien fra roten. En `addressNumber` er alltid
`county → municipality → addressCode → addressNumber`, og en full nøkkel til vegadresse 5 på veg 1234 i
kommune 4601 er `["46", "01", 1234, 5]`.

Kommunen er bare løpenummeret (`"01"`). Kommunenummeret 4601 er fylke og løpenummer sammen.

Strenger og tall skilles. `"46"` er et gyldig fylke, men `46` er det ikke, og `"1"` er ikke et gyldig
bruksnummer. Fylke og kommune er strenger for å bevare ledende nuller (`"03"`, `"01"`).

En teig uten matrikkelnummer ligger i matrikkelen under en av to plassholdere: «mnr mangler» (gnr 0, bnr 0) og
«mnr vann mangler» (gnr 0, bnr 1). Én operasjon på en `parcel` gjelder begge.

## Nøkler

Det finnes to slags nøkler.

**Pivoter** (`key` og elementene i `merge.from`) er relative til konteksten. De kan skrives som

- en skalar, som er ett segment av typen `kind`: `"key": 1234`
- en array, som er en sti av segmenter som slutter på `kind`: `"key": [12, 3]`. Første segment må være et
  barn av konteksten. På rotnivå starter stien derfor med fylket: `"key": ["46", "01", 1234]`.

**Mål** (`to`, `derivedFrom`, `into.existing`, `into.new` og `existing` og `new` i delene av `split.into`) er
alltid fulle arrays fra roten, uansett hvor i treet de står. Et mål har samme kind som pivoten i noden.

```json
{ "op": "adjust", "kind": "municipality", "key": ["46", "01"], "then": [
  { "op": "move", "kind": "addressCode", "key": 1234, "to": ["46", "02", 1234] },
  { "op": "move", "kind": "titleNumber", "key": [12, 3], "to": ["46", "02", 40, 1] } ] }
```

Konteksten for barna er kommune 4601. Veg `1234` er derfor veg 4601-1234, og `[12, 3]` er bruksnummer
4601-12/3. Målene er skrevet fullt ut, fordi et mål kan ligge hvor som helst, også i et annet fylke.

## Operasjoner

Alle noder har `op` og `kind`. Barn ligger i `then`, og pivotene deres er relative til nøkkelen til noden.

| op | felter | betydning |
|---|---|---|
| `adjust` | `key`, `then`? | Ingen endring. Gir kontekst til barna. |
| `move` | `key`, `to`, `then`? | Entiteten får ny nøkkel og beholder ID-en. |
| `merge` | `from`, `into` | Kildene i `from` utgår og peker på målet i `into`. |
| `split` | `key`, `into`, `then`? | Kilden deles i delene i `into`. |
| `create` | `key`, `derivedFrom`?, `payload`?, `then`? | Ny entitet. |

### `adjust`

`adjust` endrer ingenting. Den brukes for å nå nøkler under noe som står i ro, slik som i eksemplet over.

### `move`

```json
{ "op": "move", "kind": "addressCode", "key": ["46", "01", 1234], "to": ["46", "02", 1234], "then": [
  { "op": "move", "kind": "addressNumber", "key": 5, "to": ["46", "02", 1300, 5] } ] }
```

Veg 4601-1234 får nøkkelen 4602-1234 og beholder ID-en. Alt under vegen som ikke har en egen regel, følger
med. Vegadresse 5 har en egen regel og flyttes til veg 1300 i stedet.

Fylker og kommuner kan ikke flyttes. Bruk `merge` med `into.new` for å gi en kommune nytt nummer.

### `merge`

```json
{ "op": "merge", "kind": "municipality",
  "from": [ ["46", "01"], { "key": ["46", "03"], "then": [ … ] } ],
  "into": { "existing": ["46", "02"], "then": [ … ] } }
```

- Et element i `from` er en pivot, eller `{"key": pivot, "then": [...]}` når kilden har unntak (se
  [Unntak fra en kilde](#unntak-fra-en-kilde)).
- `into` er enten `{"existing": mål, "then"?: [...]}` eller `{"new": mål, "payload"?: {...}, "then"?: [...]}`.
- `then` under en kilde er relativ til kilden. `then` under `into` er relativ til målet.

Kildene utgår og peker på målet. Det som ligger under kildene, og som ikke har egen regel, følger med til målet.

`existing` betyr at målet **ikke opprettes av denne noden**. Målet kan finnes fra før, eller opprettes et annet
sted i reformen (se [`create` med `derivedFrom`](#create-med-derivedfrom)). Over slås 4601 og 4603 inn i 4602,
som finnes fra før og fortsetter.

`new` betyr at noden oppretter målet. Hva det betyr, avhenger av antall kilder:

- **Én kilde** er en renummerering. Målet er en ny entitet med ny ID, avledet fra kilden. Slik får en kommune
  nytt nummer:

  ```json
  { "op": "merge", "kind": "municipality", "from": [ ["46", "01"] ],
    "into": { "new": ["50", "01"], "payload": { … } } }
  ```

- **To eller flere kilder** gir et nytt mål uten én bestemt kilde. Da er `payload` påkrevd.

### `split`

```json
{ "op": "split", "kind": "municipality", "key": ["46", "05"],
  "into": [ { "new": ["46", "10"], "role": "successor", "payload": { … } },
            { "new": ["46", "11"], "payload": { … } } ],
  "then": [ … ] }
```

Kommune 4605 deles i de nye kommunene 4610 og 4611. 4605 utgår og peker på 4610 som etterfølger. `then` er
relativ til kilden og fordeler det som ligger under 4605 (se [Unntak fra en kilde](#unntak-fra-en-kilde)).

En del i `into` er ny eller eksisterende, med samme betydning som i `merge.into`:

```json
{ "new": mål, "payload"?: {…}, "role"?: "identity" | "successor", "then"?: […] }
{ "existing": mål, "role": "successor", "then"?: […] }
```

`role` avgjør hva som skjer med kilden. Høyst én del har `role`.

| del | `role` | delen | kilden |
|---|---|---|---|
| `new` | `identity` | er kilden, som flyttes hit og beholder ID-en | flyttes hit |
| `new` | `successor` | ny, avledet fra kilden | utgår og peker på delen |
| `existing` | `successor` (påkrevd) | opprettes ikke av noden | utgår og peker på delen |
| `new` | ingen | ny, avledet fra kilden | bestemmes av delen med `role` |

Har ingen del `role`, utgår kilden uten etterfølger. `"into": []` betyr at kilden utgår uten etterfølger og
uten nye deler.

`then` under en `new`-del er relativ til den nye nøkkelen og tar bare `create`. `then` under en `existing`-del
står i ro og tar alle op-er (se [Kontekster](#kontekster)).

En `existing`-del har ikke `payload`. En `identity`-del har heller ikke `payload`, fordi den er kilden selv.

Hvilke roller som gir mening, avhenger av kinden (se [Domeneregler](#domeneregler)):

- Kommuner har én peker til etterfølgeren, så en kommune som deles, må ha en del med `successor`.
- Fylker har ingen slik peker, så et fylke som deles, har ingen del med `role`.
- En veg som ikke får ny nøkkel, blir slettet. En veg deles derfor med `identity`:

  ```json
  { "op": "split", "kind": "addressCode", "key": ["46", "05", 1300],
    "into": [ { "new": ["46", "10", 1300], "role": "identity" }, { "new": ["46", "11", 1300] } ] }
  ```

  Veg 1300 flyttes til 4610 og beholder ID-en. 4611 får en ny veg 1300 avledet fra den.

- Et gårdsnummer utgår med `"into": []`, og bruksnumrene under flyttes enkeltvis:

  ```json
  { "op": "split", "kind": "landNumber", "key": ["46", "01", 12], "into": [], "then": [
    { "op": "move", "kind": "titleNumber", "key": 3, "to": ["46", "01", 40, 1] } ] }
  ```

En `existing`-del brukes når kilden skal peke på en kommune som ikke opprettes av delingen:

```json
{ "op": "split", "kind": "municipality", "key": ["50", "05"],
  "into": [ { "existing": ["50", "01"], "role": "successor" },
            { "new": ["50", "10"], "payload": { … } } ] }
```

5005 utgår og peker på 5001, som finnes fra før. 5010 er en ny kommune avledet fra 5005.

**Begrensning:** DTO-en `server` kjører, oppretter alle delene ved en kommunedeling. En kommunedeling der
etterfølgeren ikke opprettes av delingen, er gyldig i formatet, men stoppes i siste valideringssteg
(`steg: "bro"`, se [Validering](#validering)). Specen over blir derfor avvist.

### `create`

```json
{ "op": "create", "kind": "municipality", "key": ["46", "10"], "payload": { … } }
```

Uten `derivedFrom` er `create` en ny entitet uten kilde, og `payload` er påkrevd. `then` under `create` er
relativ til den nye nøkkelen.

### `create` med `derivedFrom`

`derivedFrom` er et mål (full nøkkel) av samme kind som `key`. Den nye entiteten avledes fra denne kilden og
arver data fra den. `payload` er valgfri, og overstyrer det som arves.

`derivedFrom` sier ingenting om hva som skjer med kilden. Det bestemmes av kildens egen node (`split`, `merge`
eller `move`). Har kilden ingen egen node, fortsetter den uendret.

**Utskilling:** 4610 skilles ut fra 4605, og 4605 fortsetter.

```json
{ "op": "create", "kind": "municipality", "key": ["46", "10"], "derivedFrom": ["46", "05"], "payload": { … } }
```

**Flere kilder til én ny kommune:** 4605 og 5005 utgår begge med 5010 som etterfølger. 5010 opprettes av
`create` og arver fra 4605. `existing` i `split` betyr her at 5010 opprettes et annet sted i reformen.

```json
[ { "op": "split", "kind": "municipality", "key": ["46", "05"],
    "into": [ { "existing": ["50", "10"], "role": "successor" } ] },
  { "op": "split", "kind": "municipality", "key": ["50", "05"],
    "into": [ { "existing": ["50", "10"], "role": "successor" } ] },
  { "op": "create", "kind": "municipality", "key": ["50", "10"], "derivedFrom": ["46", "05"], "payload": { … } } ]
```

**To skrivemåter for det samme:** en `new`-del i `split` gir samme resultat som en `existing`-del sammen med
en `create` med `derivedFrom` lik kilden. Det samme gjelder `merge.into.new`. Bruk `new` når målet har én
kilde, og `create` med `derivedFrom` når kilden fortsetter, eller når flere kilder peker på målet.

**Begrensning:** DTO-en kan ikke uttrykke utskilling. En spec med utskilling er gyldig i formatet, men stoppes i
`steg: "bro"`. Det samme gjelder `merge` med `into.existing` og to eller flere kilder, sammen med en `create`
med `derivedFrom` fra en av kildene. Den stoppes som `UlikTransformasjon`.

## Kontekster

Hvilke operasjoner som er lov i en `then`-liste, avhenger av hva som skjer med nøkkelen til noden over:

| kontekst | `then` under | lovlige op-er |
|---|---|---|
| står i ro | rotnivået, `adjust`, `merge.into.existing`, `split.into[]` med `existing` | alle |
| under en kilde | `move`, `split`, elementer i `merge.from` | `move`, `split`, `merge` |
| under en ny nøkkel | `create`, `merge.into.new`, `split.into[]` med `new` | `create` |

- Under en kilde følger alt uten egen regel med kilden. `adjust` og `create` gir ingen mening der.
- Under en ny nøkkel finnes ingenting fra før, så det eneste som kan skje, er at noe opprettes. Det som følger
  med fra kilden, styres med `then` under kilden.

### Unntak fra en kilde

Skal noe under en kilde behandles annerledes enn kilden, skrives regelen under kilden:

```json
{ "op": "merge", "kind": "municipality",
  "from": [ { "key": ["50", "01"], "then": [
    { "op": "move", "kind": "titleNumber", "key": [12, 3], "to": ["50", "55", 40, 1] } ] } ],
  "into": { "existing": ["50", "55"] } }
```

Kommune 5001 slås inn i 5055. Alt under 5001 følger med til 5055, bortsett fra bruksnummer 12/3, som får
nøkkelen 5055-40/1.

### Mellomledd i en sti-pivot

I en sti-pivot som `["50", "01", 12, 3]` er `50`, `5001` og `5001-12` **mellomledd**. Der nøkkelen står i ro
(for eksempel på rotnivå), betyr mellomleddene det samme som et `adjust`: de står også i ro. Under en kilde
eller en ny nøkkel er de bare en vei frem til nøkkelen.

Det samme unntaket som over, skrevet ved siden av kilden i stedet for under den, er derfor en feil:

```json
[ { "op": "move", "kind": "titleNumber", "key": ["50", "01", 12, 3], "to": ["50", "55", 40, 1] },
  { "op": "merge", "kind": "municipality", "from": [ ["50", "01"] ], "into": { "existing": ["50", "55"] } } ]
```

`move` på rotnivå sier at kommune 5001 står i ro, mens `merge` sier at 5001 utgår. Det gir feilen
`KeptAndConsumed`. Regelen gjør at hver reform har én skrivemåte.

## Payload

`county`:

```json
{ "navn": "Buskerud" }
```

`municipality`:

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

| felt | påkrevd | merknad |
|---|---|---|
| `navn` | ja | Lagres med store bokstaver. |
| `koordinatsystem` | ja | `UTM32`, `UTM33`, `UTM35` eller `UKJENT`. |
| `senterpunkt` | ja | |
| `nedsattKonsesjonsgrense` | nei | Standard `false`. |
| `godkjenteGardsnumre` | nei | Serier med `1 ≤ fra ≤ til`. |
| `adresse` | nei | `postnummer` er påkrevd i adressen. |
| `standardRekvirentOrgnummer` | nei | |
| `kommunevapen` | se merknad | Base64. Valgfri i formatet, men en ny kommune uten kommunevåpen avvises av domenereglene. |

Fylkes- og kommunenummer kommer fra nøkkelen, og ikrafttredelsesdatoen fra `ikrafttredelse`. Ukjente felter er
en feil.

## Eksempel

```json
{ "format": 1, "navn": "Reform 2027", "ikrafttredelse": "2027-01-01",
  "operasjoner": [
    { "op": "merge", "kind": "county",
      "from": ["03", { "key": "30", "then": [
        { "op": "merge", "kind": "municipality", "from": ["01", "02"],
          "into": { "new": ["33", "01"], "payload": { … } } },
        { "op": "split", "kind": "municipality", "key": "05",
          "into": [ { "new": ["33", "10"], "role": "successor", "payload": { … } },
                    { "new": ["33", "11"], "payload": { … } } ],
          "then": [
            { "op": "move", "kind": "addressCode", "key": 1234, "to": ["33", "11", 1234] },
            { "op": "split", "kind": "addressCode", "key": 1300,
              "into": [ { "new": ["33", "10", 1300], "role": "identity" }, { "new": ["33", "11", 1300] } ] } ] } ] }],
      "into": { "new": ["33"], "payload": { "navn": "Buskerud" } } },
    { "op": "adjust", "kind": "municipality", "key": ["46", "01"], "then": [
      { "op": "move", "kind": "landNumber", "key": 5, "to": ["46", "02", 7], "then": [
        { "op": "move", "kind": "titleNumber", "key": 1, "to": ["46", "02", 8, 1] } ] } ] }
  ] }
```

- Fylkene 03 og 30 slås sammen til det nye fylket 33.
- Under fylke 30 slås kommunene 3001 og 3002 sammen til den nye kommunen 3301. Kommune 3005 deles i 3310
  (etterfølger) og 3311.
- Under kommune 3005 flyttes veg 1234 til 3311. Veg 1300 deles: den beholder ID-en i 3310, og 3311 får en ny
  veg avledet fra den.
- Kommune 4601 står i ro. Gårdsnummer 4601-5 flyttes til 4602-7, bortsett fra bruksnummer 1, som flyttes til
  4602-8/1.

Eksemplet er forkortet. En ekte reform må også ha en regel for hver gyldig kommune i fylke 03 og 30 (se
[Dataregler](#dataregler)). Flere eksempler ligger i `regulering-api/reform/src/testFixtures/resources/reform-spec/valid/`.

## Validering

Specen valideres i seks steg. Valideringen stopper etter det første steget som gir feil, slik at senere steg
ikke gir følgefeil. Alle feil i det steget returneres samlet.

| `steg` | sjekker |
|---|---|
| `form` | At JSON-en har riktig form: kjent `op`, påkrevde felter, riktige typer, ingen ukjente felter. |
| `format` | Kinds, segmenter, nøkler, kontekster og payload. Alt som kan sjekkes ved å lese specen alene. |
| `konflikt` | At operasjonene ikke motsier hverandre, for eksempel to regler for samme nøkkel. |
| `domene` | At matrikkelen kan gjennomføre operasjonen for den identtypen, for eksempel at veger ikke opprettes. |
| `data` | At fylker og kommuner som brukes, finnes i matrikkelen, og at ingen kommuner blir glemt. |
| `bro` | At reformen kan uttrykkes som `Regulering`-DTO-en som `server` kjører. |

### Feilformat

```json
{ "gyldig": false,
  "feil": [ { "sti": "$.operasjoner[0].key[0]", "steg": "format", "kode": "InvalidSegment",
              "melding": "Ugyldig verdi \"3\" for county: …", "hint": "…", "flereSti": ["$.operasjoner[2]"] } ] }
```

| felt | betydning |
|---|---|
| `sti` | JSON-stien til stedet i specen feilen gjelder. Peker så nøyaktig som mulig: på segmentet (`key[1]`), feltet (`payload`) eller noden. |
| `steg` | Steget som ga feilen. |
| `kode` | Stabil kode for feilen. Bruk den, ikke meldingen, i frontend-logikk. |
| `melding` | Beskrivelse på norsk. |
| `hint` | Forslag til hva som bør skrives i stedet. Utelates når det er tomt. |
| `flereSti` | Andre steder feilen gjelder, for eksempel den andre av to noder som er i konflikt. Utelates når det er tomt. |

### `form`

Har specen feil form, gir det én feil med koden `InvalidShape` og stien fra parseren, hvis den finnes.

### `format`

| kode | sti | betyr |
|---|---|---|
| `UnknownKind` | `kind` | `kind` finnes ikke. |
| `OpNotAllowedInContext` | noden | Op-en er ikke lov i konteksten (se [Kontekster](#kontekster)). |
| `KindNotChildOfContext` | `key` eller `from[i]` | Pivoten har feil antall segmenter for konteksten, eller `kind` ligger ikke under konteksten. |
| `InvalidSegment` | segmentet | Verdien er ugyldig for kinden på den plassen (se [Kinds](#kinds)). |
| `TargetLengthMismatch` | målet | Målet er ikke en full nøkkel fra roten til `kind`. |
| `MultipleRoles` | `into[i].role` | Mer enn én del i en `split` har `role`. |
| `PayloadOnIdentity` | `into[i].payload` | En del med `role: identity` har payload. |
| `EmptyMerge` | `from` | `merge` uten kilder. |
| `PayloadRequired` | `into` eller noden | `merge` med `into.new` og flere kilder, eller `create` uten `derivedFrom`, mangler payload. |
| `PayloadNotAllowed` | `payload` | Kinden tar ikke payload. |
| `InvalidPayload` | `payload` | Payloaden kan ikke leses: manglende eller ukjente felter, feil type, ugyldig base64 og lignende. |
| `KeptAndConsumed` | der nøkkelen står i ro | En nøkkel står i ro et sted, men er kilde i en annen node (se [Mellomledd](#mellomledd-i-en-sti-pivot)). `flereSti` peker på kilden. |

Kan en nøkkel ikke leses, sjekkes ikke nodene under den.

### `konflikt`

| kode | betyr | eksempel |
|---|---|---|
| `Diverges` | Samme nøkkel er kilde i flere noder. | Kommune 4601 står både i en `merge.from` og som `key` i en `split`. |
| `Conflict` | Flere noder oppretter eller flytter noe til samme mål. | To `move` med samme `to`. |
| `SourceIsTarget` | En nøkkel er både kilde og mål. | Veg 4601-1234 flyttes til 4601-1235, og veg 4601-1235 flyttes til 4601-1236. |
| `TargetUnderSource` | Et mål ligger under en kilde, og ville selv blitt flyttet eller utgått. | Veg flyttes til `["46", "02", 1234]`, mens 4602 slås inn i 4603. |
| `SourceUnderCreatedTarget` | En kilde ligger under en nøkkel som opprettes. Der finnes ingenting fra før. | Kommune 5001 opprettes, og veg 5001-1234 flyttes. |
| `DeriveFromTarget` | `derivedFrom` peker på noe som opprettes eller flyttes hit i reformen, og som derfor ikke finnes før den. | `create` med `derivedFrom: ["46", "10"]`, der 4610 også opprettes. |

Identiske operasjoner regnes som én, så samme regel skrevet to ganger er ikke en konflikt.

### Domeneregler

Endringene skrives til matrikkelen av en **sink** per identtype i matrikkel-komreg (for eksempel `KommuneSink`
for kommuner og `VegTilbakeforingService` for veger). Domenereglene avviser operasjoner som er gyldige i
formatet, men som sinken for identtypen ikke kan gjennomføre. Hver feil har et `hint` om hva som bør skrives i
stedet.

| kind | opprette | avlede | flytte | slå sammen | utgå |
|---|---|---|---|---|---|
| `county` | ja | ja | nei ¹ | ja | ja, uten etterfølger |
| `municipality` | ja | ja | nei ¹ | ja | ja, med etterfølger ² |
| `addressCode` | nei | ja, når kilden flyttes ³ | ja | nei | nei ⁴ |
| `addressNumber`, `districtNumber`, `titleNumber` | nei | nei | ja | nei | nei |
| `landNumber` | nei | nei | ja | nei | ja, uten etterfølger, med flytting av bruksnumre ⁵ |
| `parcel` | nei | nei | ja, bare uten matrikkelnummer ⁶ | nei | nei |

Her betyr «opprette» `create`, «avlede» en ny del i `split`, `merge.into.new` eller `create` med
`derivedFrom`, «slå sammen» `merge`, og «utgå» `split` der kilden ikke fortsetter.

`districtType` støttes ikke av noen sink (`UnsupportedIdentType`).

1. Sinkene tolker samme ID med ny nøkkel som at fylket eller kommunen utgår, uten at en ny rad opprettes. Gi nytt
   nummer med `merge` og `into.new`.
2. Kommunen har én peker til etterfølgeren (`nykommuneid` i matrikkelen).
3. Sinken for veger krever at en ny veg har en kildeveg, og at kildevegen selv får ny nøkkel. Del vegen med
   `role: identity`.
4. En veg uten ny nøkkel blir slettet. Del vegen med `role: identity`.
5. Sinken for matrikkelenheter krever en ny nøkkel for hvert bruksnummer.
6. En teig med matrikkelnummer hører til matrikkelenheten, ikke bare til kommunen.

Tilleggskrav:

| kode | gjelder | krav |
|---|---|---|
| `ActionNotSupported` | alle | Handlingen er lov for kinden (tabellen over). |
| `RetireNeedsSuccessor` | `municipality` | En kommune som utgår, har etterfølger. |
| `RetireHasSuccessor` | `county`, `landNumber` | Et fylke eller gårdsnummer som utgår, har ikke etterfølger. |
| `WrongPayload` | `county`, `municipality` | Et nytt fylke har fylkespayload, og en ny kommune har kommunepayload. |
| `MissingMunicipalityCoat` | `municipality` | En ny kommune har kommunevåpen. |
| `TargetsInMultipleCounties` | `municipality`, `addressCode` | Alle mål fra én kilde ligger i samme fylke. |
| `DeriveWithoutMove` | `addressCode` | En veg som det avledes fra, flyttes selv (`role: identity`). |
| `SeriesRetireWithoutTitleMoves` | `landNumber` | Et gårdsnummer som utgår, har minst én `move` av et bruksnummer under seg. Det sjekkes ikke at alle bruksnumrene er flyttet. |
| `ParcelHasCadastralNumber` | `parcel` | Bare teiger uten matrikkelnummer flyttes. |
| `ParcelNotPaired` | `parcel` | En teig flyttes fra begge plassholderne (bnr 0 og bnr 1) til samme mål. |
| `TargetTypeMismatch` | alle | Mål og etterfølger har samme kind som kilden. |

### Dataregler

Datareglene sjekker fylker og kommuner mot data fra Matrikkel API. «Gyldig» betyr at fylket eller kommunen ikke
har utgått (`gyldigTilDato` er tom). Fylker og kommuner slettes aldri fra matrikkelen. De får bare en
`gyldigTilDato`.

| kode | krav |
|---|---|
| `MunicipalityNotCovered` | Et fylke som utgår, har en egen regel for hver gyldig kommune i fylket. |
| `TargetNotFound`, `TargetExpired` | Et fylke eller en kommune som er mål, og som ikke opprettes i reformen, finnes og er gyldig. |
| `TargetInUse` | Et fylke eller en kommune som opprettes, har ikke et nummer som er i bruk av et gyldig fylke eller en gyldig kommune. Et nummer som har utgått, kan brukes på nytt. |
| `SourceNotFound`, `SourceExpired` | Et fylke eller en kommune som er kilde eller `derivedFrom`, finnes og er gyldig. |

`MunicipalityNotCovered` er nødvendig fordi en kommune uten egen regel følger fylket sitt. Hvis fylke 30 slås inn
i 33, ville kommune 3001 da fått nøkkelen 3301 med samme ID. Det er en flytting av en kommune, som sinken ikke
støtter (se fotnote 1 over). Regelen for fylket dekker ikke kommunene, så hver kommune må stå i en egen
`merge` eller `split`.

Datareglene sjekker bare fylker og kommuner. Om veger, adresser, gårdsnumre og teiger finnes, sjekkes ikke. Det
sjekkes heller ikke at alt under en kommune som deles, eller alle bruksnumre under et gårdsnummer som utgår, har
fått en regel.

Kommunedataene hentes ved oppstart av `regulering-api`, og valideringen laster dem ikke på nytt. En kommune som
er opprettet eller har utgått etter oppstart, er derfor ikke kjent med riktig status før tjenesten startes på
nytt.

### `bro`

Til slutt oversettes reformen til `Regulering`-DTO-en som `server` kjører. DTO-en kan uttrykke mindre enn
formatet, så en spec som har bestått alle andre steg, kan stoppes her. Specen lagres ikke da.

| kode | betyr |
|---|---|
| `IngenDtoEkvivalent` | En operasjon kan ikke uttrykkes i DTO-en. Meldingen sier hvorfor. |
| `FinnesIkke` | Et fylke eller en kommune som ikke opprettes i reformen, finnes ikke. |
| `UgyldigPayload` | Payloaden for et nytt fylke eller en ny kommune mangler noe DTO-en krever. |
| `UlikTransformasjon` | DTO-en gir ikke de samme reglene som specen. Dette er en intern rundturssjekk. Kjent årsak er listet under. Ellers tyder den på en feil i backend. |

Kjente tilfeller som stoppes her:

- En kommunedeling der etterfølgeren ikke opprettes av delingen (en `existing`-del).
- Utskilling, der kilden fortsetter (`create` med `derivedFrom` fra en kilde uten egen node).
- En kommunedeling med færre enn to deler.
- `merge` med `into.existing` og to eller flere kilder, sammen med en `create` med `derivedFrom` fra en av kildene
  (stoppes som `UlikTransformasjon`).

## Endepunkter i `regulering-api`

Specen lagres som `jsonb` i tabellen `reform`, med `navn` og `ikrafttredelse` i egne kolonner for listing.
`Regulering`-DTO-en lagres ikke. Den lages fra specen ved hvert kall, fordi kommunedataene den bygger på kan
endre seg.

| metode og sti | gjør | svar |
|---|---|---|
| `POST /api/reguleringer/validering` | Validerer uten å lagre. | 200 med `{gyldig, feil}` |
| `POST /api/reguleringer` | Validerer og lagrer. | 201 med `{id}`, eller 422 med `{gyldig, feil}` |
| `GET /api/reguleringer` | Lister `{id, navn, ikrafttredelse}`. | 200 |
| `GET /api/reguleringer/{id}` | Henter specen. | 200, eller 404 |
| `PUT /api/reguleringer/{id}` | Validerer og erstatter specen. | 204, 422 eller 404 |
| `DELETE /api/reguleringer/{id}` | Sletter. | 204, eller 404 |
| `GET /api/reguleringer/{id}/regulering` | Gir `Regulering`-DTO-en for specen, med `id` som streng. | 200, 404, eller 422 med `{gyldig, feil}` |

- Bare gyldige specer lagres.
- En `id` som ikke er et heltall, gir 400.
- `PUT` validerer før den ser etter specen, så en ugyldig spec gir 422 også når `id` ikke finnes.
- `GET /{id}/regulering` validerer specen på nytt. Den kan gi 422 selv om specen var gyldig da den ble lagret,
  for eksempel hvis en kommune har utgått siden.
- `regulering-api` sender ikke DTO-en til `server`. Det må gjøres separat.
