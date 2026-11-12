# Reform-format: design og implementasjon

Dette dokumentet er for utviklere av backend. Det forklarer hvordan en spec valideres og oversettes, og hvorfor
formatet er bygd slik det er. Selve formatet er beskrevet i [reform-format.md](reform-format.md), og regelmodellen
specen oversettes til, i [reform-op.md](reform-op.md). Modulene er beskrevet i [arkitektur.md](arkitektur.md#moduler).

## Mål

Frontend skal kunne beskrive en hel reform (sammenslåinger, delinger og flyttinger av fylker, kommuner, veger,
gårdsnumre og så videre) i ett dokument som

- skrives av frontend direkte, uten en Kotlin-DSL i backend
- lagres som kilden til sannhet, mens `Regulering`-DTO-en som `server` kjører, lages fra det
- bruker de samme fem operasjonene (`adjust`, `move`, `merge`, `split` og `create`) for alle identtyper, i
  stedet for én endringstype per identtype slik DTO-en har.

## Kjeden

```
JSON
 → ReformSpec                       Kotlin-typer som speiler JSON 1:1, uten tolkning
 → resolve(spec, SegmentRegistry)   tolker treet og lager en flat liste med ReformOp
 → ReformClaims.validate            konflikter mellom ReformOp-ene
 → validateDomain                   hva sinkene i matrikkel-komreg støtter, uten IO
 → validateData(kommuneService)     regler som trenger data om fylker og kommuner
 → toReguleringDTO                  oversetter til Regulering-DTO og sjekker rundturen
```

`ReformSpecService` i `:regulering-api:server` kjører kjeden. Hvert steg samler alle sine feil, og kjeden
stopper etter det første steget som feiler.

Fra og med `ReformClaims.validate` gjelder feilene `ReformOp`-er, ikke steder i specen. `resolve` returnerer
derfor `Resolved(ops, origins)`, der `origins` gir JSON-stien hver `ReformOp` kom fra. Slik peker alle feil
som sendes til frontend, på et sted i specen.

### Hvor reglene hører hjemme

| regelen gjelder | steg | kode |
|---|---|---|
| Det som kan sjekkes ved å lese specen alene: form, segmenter, kontekster, `KeptAndConsumed` | `format` | `:regulering-api:reform`, pakken `format` |
| Operasjoner som motsier hverandre | `konflikt` | `ReformClaims` i `:lib:transformation` |
| Hva sinken for hver identtype kan gjennomføre | `domene` | `:regulering-api:reform`, pakken `rules` |
| Hva som finnes i matrikkelen (bare fylker og kommuner) | `data` | `:regulering-api:reform`, pakken `rules`, med `KommuneService` |
| Hva DTO-en og den eksisterende kjøringen kan uttrykke | `bro` | `toReguleringDTO` i `:lib:regulering-mapping` |

En ny regel plasseres i det første steget som har informasjonen den trenger. Broen avviser mye av det samme som
domenereglene, men senere og med meldinger som handler om DTO-en, ikke om formatet. Domenereglene er derfor en
tabell per kind (`SinkCapabilities.kt`) med et hint per handling som ikke støttes.

## Designvalg

### Hierarkisk og uavhengig av identtype

Formatet kjenner ikke til kommuner eller veger, bare **kinds** og hvilken forelder hver kind har. En ny identtype
er en ny kind i registeret, ikke en ny operasjon.

Hver kind har én fast forelder. Da avgjør `kind` hele stien fra roten, og dermed identtypen. Derfor kan en pivot
skrives kort, som en skalar under en kontekst, og kindene til segmentene i en sti-pivot kan utledes baklengs fra
`kind`.

### Specen lagres, ikke `ReformOp` eller DTO-en

- `ReformOp` er en flat liste. Den har ikke hierarkiet, kortformene eller `adjust`, og en `ReformOp` vet ikke
  hvor i specen den kom fra. Det er specen frontend skal kunne åpne og redigere, så det er den som lagres.
- DTO-en lages på nytt ved hvert kall, fordi den inneholder data fra `KommuneService` (for eksempel data om
  kommuner som utgår) som kan endre seg. Hvis `regulering-api` skal sende DTO-en til `server`, bør den lagres
  sammen med sendingen.
- `resolve` lager `ReformOp` direkte, uten et mellomlag.

### Relative pivoter og absolutte mål

- **Pivoter** (`key`, `merge.from`) er relative til konteksten, så en reform med mange endringer under én kommune
  slipper å gjenta fylke og kommune.
- **Mål** (`to`, `derivedFrom`, `existing`, `new`) er absolutte, fordi et mål kan ligge hvor som helst, også i et
  annet fylke enn konteksten.
- Segmentverdier er JSON-strenger eller -tall, og kinden avgjør hvilken. Fylke og kommune er strenger for å
  bevare ledende nuller. At feil type gir `InvalidSegment` med sti, gjør feilen lett å finne.

### Én skrivemåte per reform

Mellomleddene i en sti-pivot betyr det samme som `adjust` der nøkkelen står i ro, og ellers ingenting. Et unntak
fra en kilde må derfor skrives under kilden (`merge.from[].then`, `split.then`, `move.then`). Skrevet ved siden av
kilden ville samme nøkkel både stå i ro og være kilde, og det gir `KeptAndConsumed`. Uten denne regelen kunne den
samme reformen skrives på flere måter, og specer ville vært vanskeligere å lese og sammenligne.

`format` sjekker bare det `ReformOp` ikke kan uttrykke: at en nøkkel som står i ro (`adjust`, mellomledd i en
kontekst som står i ro, `existing`), ikke samtidig er kilde. Alle andre konflikter sjekkes av `ReformClaims`.

### `existing`, `new` og `role`

`merge.into` og delene i `split.into` har samme form: en nøkkel som er `new` eller `existing`.

- `existing` betyr «opprettes ikke av denne noden», ikke «finnes fra før». Målet kan opprettes et annet sted i
  reformen. Det tilsvarer `ReformClaims.requiredExistingTargets`, som er de svake målene minus det som opprettes.
  Slik kan flere kilder ha samme etterfølger.
- En `existing`-del har ikke `payload`, fordi noden ikke oppretter den.
- `role` på en `existing`-del kan bare være `successor`. `identity` betyr at kilden flyttes til en ledig nøkkel, og
  passer ikke for et mål som allerede er tatt.
- `role` er påkrevd på en `existing`-del. Uten rolle ville delen ikke gitt noen `ReformOp`, og ikke hatt noen
  virkning. Kravet kan fjernes senere uten å gjøre eksisterende specer ugyldige.
- Disse reglene håndheves av JSON-typene (`PartSpec`, `ExistingRole`), så brudd gir feil i steget `form`.

### `create` med `derivedFrom`

`derivedFrom` gir en `Derive` i stedet for en `Create`. Det er ikke en kilde i formatets forstand: nøkkelen står
ikke i ro, og noden bestemmer ikke hva som skjer med den. Det bestemmes av kildens egen node, og uten en slik node
fortsetter kilden.

`Derive.from` er én nøkkel. En ny entitet med bidrag fra flere kilder uttrykkes derfor ved at hver kilde peker på
den (som etterfølger eller med `merge`), mens `create` med `derivedFrom` avgjør hvilken kilde den arver fra.

En `new`-del i `split` gir de samme `ReformOp`-ene som en `existing`-del sammen med `create` med `derivedFrom` lik
kilden. Ingen regel håndhever hvilken skrivemåte som brukes.

## Fra spec til `ReformOp`

Tabellen viser hvilke `ReformOp`-er `resolve` lager for hver node. `k` er pivoten, `s₁ … sₙ` kildene i
`merge.from`, `t` målet i `merge.into`, `p` delen med `role`, `q` hver `new`-del uten `role` og `d` er
`derivedFrom`.

| node | `ReformOp` |
|---|---|
| `adjust` | ingen |
| `move` | `Move(k, to)` |
| `merge` med `into.existing` | `RetireTo(sᵢ, t)` for hver kilde |
| `merge` med `into.new`, én kilde | `RetireTo(s, t)` og `Derive(t, from = s, payload)` |
| `merge` med `into.new`, flere kilder | `RetireTo(sᵢ, t)` for hver kilde og `Create(t, payload)` |
| `split` der `p` har `role: identity` | `Move(k, p)` og `Derive(q, from = k, payload)` |
| `split` der `p` er en `new`-del med `role: successor` | `Retire(k, successor = p)`, `Derive(p, from = k, payload)` og `Derive(q, from = k, payload)` |
| `split` der `p` er en `existing`-del | `Retire(k, successor = p)` og `Derive(q, from = k, payload)` |
| `split` uten `role` | `Retire(k)` og `Derive(q, from = k, payload)` |
| `create` | `Create(k, payload)` |
| `create` med `derivedFrom` | `Derive(k, from = d, payload)`, der `payload` kan mangle |

`merge` med én kilde og `into.new` er en renummerering: `RetireTo` sier at kilden utgår og peker på målet, og
`Derive` at målet er en ny entitet med ny ID og data fra kilden.

Oversettelsen gir de samme `ReformOp`-ene som `Reguleringsinput.toReformOps()` gir for den tilsvarende DTO-en.
Det er det som gjør at rundturssjekken i `toReguleringDTO` kan sammenligne dem. Testene i
`ResolveRoundTripTest` sjekker dette.

## Segmentregister og nøkler

`SegmentRegistry` (`format/V1Segments.kt`) inneholder kindene. For hver kind står navnet, forelderen, domenetypen,
komponentene kinden bidrar med i `Ident`, en dekoder fra JSON og eventuelt en payload-dekoder. Kind-stien gir
samme `IdentType` som `ReformMapping.kt` og broen bruker.

| kind | domenetype | komponent i `Ident` |
|---|---|---|
| `county` | `CountyNumber` | `Fylkesnummer` |
| `municipality` | `MunicipalitySerial` | `Kommunenummer.Lopenummer` |
| `addressCode` | `AddressCode` | `Adressekode` |
| `addressNumber` | `AddressNumber` | `Adressenummernummer` |
| `districtType` | `DistrictType` | `Kretstype` |
| `districtNumber` | `DistrictNumber` | `Kretsnummer` |
| `landNumber` | `LandNumber` | `Matrikkelnummer.Gardsnummer` |
| `titleNumber` | `TitleNumber` | `Matrikkelnummer.Bruksnummer` |
| `parcel` | `ParcelId` | gnr 0, bnr 0 eller 1, fnr 0, snr 0 og `TeigId` |

I `resolve` bygges en nøkkel ett segment om gangen: `Key.Empty` er roten, og `Key.Snoc(forelder, kind, verdi)` er
forelderen med ett segment til. Konteksten til en node er nøkkelen til noden over. `Key.toIdents(registry)` gir
identene nøkkelen tilsvarer.

De fleste nøkler gir én ident, men `parcel` gir to. En teig uten matrikkelnummer er en `Ident` med syv komponenter,
og ligger under enten bnr 0 («mnr mangler») eller bnr 1 («mnr vann mangler»). DTO-en flytter alltid begge, så
`parcel` gir én ident for hver. Når en node har både kilde og mål, pares identene etter indeks. Komponentene lages
direkte fra typene i core-api, fordi domenetypene `LeaseNumber` og `UnitNumber` ikke tillater 0.

### Nummerering av fylker og kommuner

`NumberingScheme` avgjør hvilke fylkesnummer som er gyldige. Den settes med `numberingScheme` i
`application.conf`, eller med miljøvariabelen `APP_NUMBERING_SCHEME`.

| verdi | fylkesnummer | kommunenummer |
|---|---|---|
| `production` (standard) | 2 siffer | 4 siffer |
| `test` | 2 eller 5 siffer | 4 eller 7 siffer |

Testdatabasen har genererte testfylker med nummer fra 10000. Løpenummeret (`MunicipalitySerial`) har alltid 2
siffer. `MunicipalityNumber` lagres som én streng, og deles i fylke og løpenummer ved de to siste sifrene.

## Bakgrunn for domenereglene

Tabellen i [reform-format.md](reform-format.md#domeneregler) er utledet fra sinkene i matrikkel-komreg
(`no.statkart.matrikkel.komreg.mottaker`: `FylkeSink`, `KommuneSink`, `VegTilbakeforingService` og tilsvarende for
vegadresse, matrikkelenhet, teig og krets), ikke fra valideringen av DTO-en.

- Sinkene for vegadresse, krets, matrikkelenhet og teig forutsetter at hver transformasjon har en eksisterende
  kilde (`sourceEntity`) og en ny nøkkel (`transformedIdent`). Derfor støttes bare `move` for disse.
- `VegTilbakeforingService` tolker en transformasjon uten ny nøkkel som at vegen skal slettes. En veg som skal
  bestå, må derfor flyttes, også når den deles (`role: identity`).
- Fylker og kommuner slettes aldri. De får en `gyldigTilDato`. Derfor kan datareglene kreve at kilder og mål finnes
  og er gyldige.
- En kommune som ikke har en egen regel når fylket utgår, følger fylket med samme ID og ny nøkkel. `KommuneSink`
  støtter ikke det, og vil enten feile eller la kommunen peke feil. Derfor finnes `MunicipalityNotCovered`.

## Kommunecache

`MatrikkelApiMunicipalityCache` i `:regulering-api:server` implementerer `KommuneService` for regulering-api, med
data fra Matrikkel API. Feltene tolkes som i `KommuneServiceImpl` i matrikkel-komreg (se `MunicipalityMapping.kt`).

- Fylker, kommuner, postnummerområder og standardrekvirenter lastes ved oppstart.
- Slås det opp en kommune som ikke finnes i cachen, lastes alt på nytt én gang. Fylker og kommuner slettes aldri,
  så det som mangler, kan bare være nytt.
- Det finnes ingen annen fornyelse. En kommune som har utgått etter siste lasting, regnes fortsatt som gyldig.
- Kommunevåpen hentes første gang en kommune trenger det, og beholdes. Det er ikke målt hvor lang tid det tar å
  hente alle ved oppstart.
- `findAlleKommuner()` gir kommuner med våpen, og henter derfor alle som mangler. Broen bruker den (sammen med
  `findAlleFylker()`) når en reform berører fylker eller kommuner, så da hentes alle våpen. Datareglene trenger
  ikke våpen, og bruker `withoutCoatOfArms`.
- Validering av en reform laster aldri cachen på nytt. Det gjør bare oppslag av en ukjent kommune-ID gjennom
  `get(MunicipalityId)`, som brukes av andre ruter i `regulering-api`. En kommune som opprettes i matrikkelen etter
  oppstart, gir derfor `SourceNotFound` eller `TargetNotFound` til tjenesten startes på nytt.

## Avgrensninger

- Den eksisterende kjøringen i `server` (`mapInput` og `IdentTransformer`) er uendret. Ingen `ReformOp` kjøres
  direkte.
- `regulering-api` sender ikke DTO-en til `server`.
- Formatet kan uttrykke mer enn DTO-en. En spec som stoppes i `bro`, har bestått alle andre steg, men lagres ikke.
  Dette gjelder blant annet utskilling og kommunedeling til en eksisterende kommune.
- Det sjekkes ikke at alt under en kommune som deles (veger, adresser, matrikkelenheter), eller alle bruksnumre
  under et gårdsnummer som utgår, har fått en regel. Det krever en tørrkjøring mot matrikkelen. Et bruksnummer
  uten egen regel får i dag en delvis nøkkel (bare bruksnummeret) i `IdentTransformer`.
- Datareglene sjekker bare fylker og kommuner.

## Åpne spørsmål

- Skal ugyldige utkast kunne lagres, for eksempel med `gyldig: false` i databasen, slik at brukeren kan fortsette
  senere? Nå lagres bare gyldige specer.
- Navnet `existing` kan misforstås, fordi det betyr «opprettes ikke av denne noden», ikke «finnes fra før». Et
  nytt navn må innføres i `merge` og `split` samtidig.
- Skal kommunecachen fornyes med jevne mellomrom, slik at kommuner som utgår, oppdages uten omstart?
- Skal teig-identene fortsatt lages direkte av komponentene i core-api, eller skal domenetypene tillate 0?
