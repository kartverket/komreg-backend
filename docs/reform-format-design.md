# Reform-format: designvalg

Dette dokumentet forklarer hvorfor reform-formatet og kjeden rundt det er som de er. Selve formatet er
beskrevet i [reform-format.md](reform-format.md), modellen det oversettes til i [reform-op.md](reform-op.md),
og modulene i [arkitektur.md](arkitektur.md#moduler).

## Mål

Et hierarkisk, identtype-agnostisk JSON-format for reguleringer («reform-spec») som

- skrives av frontend. Backend har ingen Kotlin-DSL og ikke noe typet op-tre for formatet.
- lagres som kilde til sannhet, mens `Regulering`-DTO-en avledes av det.
- kan uttrykke alt `Regulering`-DTO-en kan uttrykke, med noen få prinsipielle grep (`move`, `merge`, `split`,
  `adjust`, og `create`) i stedet for én transformasjonstype per identtype.

## Kjeden

```
JSON
 → ReformSpec                       speiler JSON 1:1, uten tolkning
 → resolve(spec, SegmentRegistry)   én gjennomgang av treet: Resolved(ops, origins)
 → ReformClaims.validate            konflikter mellom op-er
 → validateDomain                   hva sinkene støtter, ingen IO
 → validateData(kommuneService)     regler som trenger oppslag
 → toReguleringDTO                  bro til DTO, med rundturssjekk
```

Hvert steg samler alle sine feil, og kjeden stopper etter første steg som feiler. Feil fra `ReformClaims`,
domeneregler, dataregler og broen gjelder `ReformOp`-er. `Resolved.origins` oversetter dem til JSON-stier i
specen, slik at alle feil som rapporteres til klienten, peker på et sted i det frontend skrev.

### Hvor reglene bor

| regel gjelder | steg | hjem |
|---|---|---|
| Det som er synlig i specen alene (form, segmenter, kontekst, `KeptAndConsumed`) | `format` | `:regulering-api:reform`, `format` |
| Op-er som motsier hverandre (to regler for samme kilde, mål under kilde og lignende) | `konflikt` | `ReformClaims`, `:lib:transformation` |
| Hva sinkene i matrikkel-komreg støtter per identtype | `domene` | `:regulering-api:reform`, `rules` |
| Hva som finnes i matrikkelen (fylker og kommuner) | `data` | `:regulering-api:reform`, `rules`, med `KommuneService` |
| Hva DTO-en og legacy-kjøringen kan uttrykke | `bro` | `:lib:regulering-mapping` |

Ny regel plasseres i det første steget som har informasjonen den trenger. Broen er siste sikring og slår til for
det samme som domeneregler fanger, bare senere og med dårligere meldinger. Domenereglene er derfor en tabell per
kind (`SinkCapabilities.kt`), ikke spredt `when`-logikk, og meldingene foreslår riktig grep i formatet.

## Designvalg

### Hierarkisk og identtype-agnostisk

Formatet vet ikke hva en kommune eller en veg er, bare hvilke **kinds** som finnes og hvordan de henger sammen.
Med fem operasjoner for alle identtyper blir en ny identtype en ny kind i registeret, ikke en ny op.

Hver kind har **én fast forelder**. Derfor avgjør `kind` hele stien fra roten, og dermed identtypen. Pivoter
kan skrives kort (en skalar under en kontekst), mens kindene i en sti utledes baklengs via forelderkjeden.

### Spec lagres, ikke `ReformOp` eller DTO

- `ReformOp` er flatt. `adjust` gir ingen op, hierarkiet og kortformene er borte, og en op har ingen sti. Feil
  kan ikke føres tilbake til et sted i det frontend skrev uten specen, så specen er det som lagres.
- DTO-en lagres ikke. Den avledes ved hvert kall, fordi dataene fra `KommuneService` kan endre seg. Et
  øyeblikksbilde bør først tas når sending til `server` eventuelt innføres.
- `resolve` lager `ReformOp` direkte. Det er ikke noe mellomlag (for eksempel egne steg-typer) mellom specen og
  `ReformOp`.
- Lowering gir de samme `ReformOp`-ene som `Reguleringsinput.toReformOps()` gir for dagens DTO-er. Det er det som
  gjør at rundturssjekken i `toReguleringDTO` holder.

### Nøkler: relative pivoter og absolutte mål

- **Pivoter** (`key`, `merge.from`) er relative til konteksten, så et tre med mange noder under én kommune ikke
  gjentar fylke og kommune.
- **Mål** (`to`, `derivedFrom`, `existing`, `new`) er absolutte, fordi et mål kan ligge hvor som helst, også i
  et annet fylke. Konflikter mellom mål og kilder sjekkes globalt av `ReformClaims`.
- Verdier er JSON-strenger eller -tall og dekodes av kinden. `"46"` er et fylke, `46` er det ikke. Det gjør at
  ledende nuller bevares og at feil type fanges som `InvalidSegment` og ikke som et unntak.

### Mellomledd og kontekster

Leddene foran det siste i en sti-pivot betyr det samme som et `adjust`, der `adjust` er lov, og ellers ingenting.
Valget gir én kanonisk skrivemåte per reform: unntak fra en kilde skrives under kilden
(`merge.from[].then`, `split.then`, `move.then`), ikke ved siden av. Ellers ville samme nøkkel stå både i ro og
som kilde. Et eksplisitt `adjust` er derfor bare lov der nøkkelen står i ro. Under en kilde følger alt uten egen
regel kilden uansett.

Formatet sjekker bare det `ReformOp` ikke kan uttrykke: at en nøkkel som står i ro (`adjust`, mellomledd på roten,
`into.existing`) ikke samtidig er kilde i en annen node. Alt annet overlates til `ReformClaims`.

### `existing`, `new` og `role`

`merge.into` og `split.into[]` har samme form: en nøkkel som er `new` eller `existing`.

- `existing` betyr «opprettes ikke av denne noden», ikke «finnes fra før». Målet kan opprettes et annet sted i
  reformen. Det er slik `ReformClaims.requiredExistingTargets` også virker: svake mål minus det som opprettes.
  Dermed kan flere kilder dele samme etterfølger.
- En `existing`-del har ikke `payload`.
- `role` på en `existing`-del tar bare `successor`. `identity` betyr at kilden flyttes til en nøkkel som er
  ledig, og gir ingen mening for et mål som finnes fra før.
- `role` er påkrevd på `existing`. En eksisterende del uten rolle gir ingen `ReformOp` og ville ikke hatt noen
  effekt. Kravet kan løses opp senere uten å bryte eksisterende specer.
- Brudd på disse reglene er formfeil (ukjent felt, ukjent enum-verdi, manglende felt), ikke egne formatregler.
- Høyst én del har `role`, og `identity` har ikke payload.

### `create` med `derivedFrom`

`derivedFrom` er ikke en kilde i formatets forstand. Det står ikke i ro, og det bestemmer ikke hva som skjer med
kilden. Det gjør kildens egen op, og uten en slik op fortsetter kilden. `Derive.from` forblir én nøkkel, så en ny
entitet med bidrag fra flere kilder uttrykkes med etterfølger eller flyttinger i kildenes egne op-er.

`new` er en kortform for `existing` sammen med en `create` med `derivedFrom`, og det samme gjelder `merge.into.new`
med flere kilder. Begge skrivemåtene gir like `ReformOp`-er, og ingen regel håndhever valget. Bruk `new` når målet
har én kilde, og `create` med `derivedFrom` når kilden fortsetter eller flere kilder peker på målet.

### Lowering til `ReformOp`

| op | `ReformOp` |
|---|---|
| `adjust` | ingen |
| `move` | `Move(k, to)` |
| `merge` med `existing` | `RetireTo(s_i, t)` for hver kilde |
| `merge` med `new`, én kilde | `RetireTo(s, t)` og `Derive(t, from = s, payload)` (renummerering, ny ID) |
| `merge` med `new`, to eller flere kilder | `RetireTo(s_i, t)` for hver kilde og `Create(t, payload)` |
| `split`, del med `role: identity` p | `Move(k, p)` og `Derive(q, from = k, payload)` for de øvrige `new`-delene |
| `split`, `new`-del med `role: successor` p | `Retire(k, successor = p)` og `Derive(q, from = k, payload)` for alle `new`-delene |
| `split`, `existing`-del med `role: successor` p | `Retire(k, successor = p)` og `Derive(q, from = k, payload)` for `new`-delene |
| `split`, ingen del har `role` | `Retire(k)` og `Derive(q, from = k, payload)` for alle delene |
| `create` | `Create(k, payload)` |
| `create` med `derivedFrom` d | `Derive(k, from = d, payload?)` |

## Segmentregister, nøkler og nummerering

`SegmentRegistry` (`format/V1Segments.kt`) inneholder kindene. Hver kind har navn, forelder, domenetype, hvilke
komponenter den bidrar med i `Ident`, en dekoder, og valgfri payload. Registeret mapper kind-stien til samme
`IdentType` som `ReformMapping.kt` og broen bruker.

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

I resolveren er en nøkkel en heterogen snoc-liste (`Key.Empty`, `Key.Snoc(init, kind, value)`). Konteksten til en
node er nøkkelen til forelderen. `Key.toIdents(registry)` gir identene nøkkelen svarer til.

`parcel` er et spesialtilfelle. En teig er syv komponenter i `Ident`, og DTO-en flytter alltid både bnr 0 og
bnr 1. En kind kan derfor **ekspandere** en nøkkel til flere identer: én for alle kinds unntatt `parcel`, som gir
to. Lowering parer kilde- og målekspansjonen etter indeks. Komponentene lages direkte og bruker ikke `LeaseNumber`
og `UnitNumber`, som ikke tillater 0.

`NumberingScheme` (`Production` eller `Test`, konfigurert med `numberingScheme` i `application.conf` eller
`APP_NUMBERING_SCHEME`) avgjør hvilke fylkesnummer som er gyldige:

- Prod: fylkesnummer har 2 siffer.
- Test: testdatabasen har genererte testfylker fra 10000, så fylkesnummer har 2 eller 5 siffer.
- Løpenummeret (`MunicipalitySerial`) har alltid 2 siffer. Et kommunenummer har derfor 4 siffer i prod og 4 eller
  7 i test. `MunicipalityNumber` serialiseres som én streng, og parsing deler den ved de to siste sifrene.

## Domeneregler: bakgrunn

Tabellen i [reform-format.md](reform-format.md#domeneregler) er utledet fra sinkene i matrikkel-komreg
(`no.statkart.matrikkel.komreg.mottaker`: `FylkeSink`, `KommuneSink`, `VegTilbakeforingService` og de tilsvarende
for vegadresse, matrikkelenhet, teig og krets), ikke fra DTO-valideringen. Sinkene for vegadresse, krets,
matrikkelenhet og teig forutsetter ny nøkkel (`transformedIdent`) og eksisterende kilde (`sourceEntity`).
`VegTilbakeforingService` tolker en `null` i `transformedIdent` som sletting, så en veg som skal bestå med ny
nøkkel, må deles med `role: identity`.

Kommuner og fylker slettes aldri, de får bare `gyldigTilDato`. Derfor kan datareglene kreve at mål og kilder
finnes og er gyldige. Et fylke som utgår, må ha en egen regel for hver gyldig kommune. Ellers følger kommunen
kaskaden med samme ID og ny nøkkel, og `KommuneSink` krasjer eller peker feil.

## Bevisste avgrensninger

Formatet kan uttrykke mer enn dagens kjøring, og broen avviser det som ikke kan uttrykkes i DTO-en. En spec som
stoppes i `bro`, har bestått alle andre steg og lagres ikke. Det er riktig oppførsel inntil videre.

- Legacy-kjøringen (`mapInput`, `IdentTransformer`) og `ReformOp`/`ReformClaims` er uendret. Ingen `ReformOp`
  kjøres direkte.
- Sending av DTO til `server` og kjøring ligger utenfor `regulering-api`.
- Dekning av veger, adresser og matrikkelenheter under en kommune som deles, og av bruksnumre under et
  gårdsnummer som utgår, hører til en tørrkjøring og sjekkes ikke.
- En `existing`-del uten rolle er ikke støttet, fordi den ikke ville gitt noen `ReformOp`. Det er en mulig
  utvidelse, sammen med en regel om at målene i `split.then` må ligge under en av delene.
- `format` er `1`. Endringer i formen av `split.into` og `create` er gjort uten migrering, fordi formatet er nytt.

## Åpne spørsmål

- Skal ugyldige utkast kunne lagres, med `gyldig: false` i databasen, så brukeren kan fortsette senere? Nå
  lagres bare gyldige specer.
- Navnet `existing` kan villede, siden det betyr «opprettes ikke av denne noden». Et eventuelt nytt navn må endres
  i `merge` og `split` samtidig.
- Kommunecachen kan være utdatert til neste omstart. Neste steg er i så fall å laste på nytt med jevne mellomrom (TTL).
- Om `parcel` skal bruke core-komponentene direkte (slik nå) er ikke endelig bekreftet.
