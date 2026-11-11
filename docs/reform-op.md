# ReformOp: et språk for reguleringsregler

`ReformOp` beskriver hva som skjer med **forretningsnøkler** (`Ident`) i en regulering.
`ReformClaims` validerer at reglene ikke motsier hverandre, og slår opp den mest
spesifikke regelen for en nøkkel. Dette er et alternativt *språk for regler*, ikke
en erstatning for dagens transformasjonsmotor: `Regulering`-DTO-en,
`Reguleringsinput`, `mapInput` og `IdentTransformer` styrer fortsatt kjøringen.
Ingen eksisterende endepunkter tar imot `ReformOp`, og ingen regler i en lagret
regulering valideres automatisk som `ReformClaims`.

## Nøkler, identitet og eierskap

En `Ident` kan ha flere komponenter i rekkefølge, for eksempel fylke, kommune
og gårdsnummer. En regel for kommunen gjelder også nøkler under kommunen,
med mindre et mer spesifikt oppslag treffer, for eksempel en regel for
gårdsnummerserien. `Ident` er ikke entitetens `Id`: en nøkkel kan endres uten
at den samme entiteten får ny ID. Operasjonene uttrykker den tilsiktede
semantikken til språket; **legacy-motoren utfører ikke operasjonene direkte**.

| Operasjon | Betydning i språket |
|---|---|
| `Create(target, payload)` | Ny entitet uten kilde. Payload er påkrevd; eier målnøkkelen alene. |
| `Derive(target, from, payload?)` | Ny entitet avledet fra en eksisterende nøkkel, med ny ID. Bestemmer ikke hva som skjer med kilden. |
| `Move(source, target)` | Samme entitet og ID får ny nøkkel; etterkommere uten egen regel følger med. Eier både kilde og mål. |
| `Retire(source, successor?)` | Kilden utgår. Ved deling kan én etterfølger få peker fra kilden; øvrige deler beskrives med `Derive`. |
| `RetireTo(source, target)` | Kilden utgår med peker til mål som finnes eller opprettes. Flere kilder kan peke på samme mål. |

`Create` og `Derive` er `NewEntity` og eier målet eksklusivt. `RetireTo.target`
og `Retire.successor` er svake, delbare mål: de oppretter ikke målet selv.
`Move` betyr identitetsbevaring; `RetireTo` betyr en relasjon fra utgått
entitet til en annen, ikke flytting av samme ID. Ved `Retire` uten eksplisitt
regel for en etterkommer er plasseringen ikke entydig; en fremtidig motor må
håndtere delvise nøkler. Dagens legacy-kjøring har sin egen logikk for dette.

For eksempel gir én-til-én-erstatning av en kommune typisk
`RetireTo(gammel, ny)` sammen med `Derive(ny, from = gammel, payload)`.
Sammenslåing av to kommuner til en ny kan uttrykkes med
`RetireTo(a, ny)`, `RetireTo(b, ny)` og én `Create(ny, payload)`.
Deling av en kommune i `b` og `c` kan uttrykkes med
`Retire(a, successor = b)` og `Derive(b, from = a, ...)` /
`Derive(c, from = a, ...)`. Fylkesdeling har ingen etterfølger. Dette er
beskrivelser av regler, **ikke** løfter om at legacy-kjøringen oppfører seg
som en motor for disse operasjonene.

## Validering av reglene

Bruk `ReformClaims.validate(ops)` før reglene brukes. Den fjerner identiske
duplikater, samler alle strukturelle feil i en `NonEmptyList<ReformErr>` og
returnerer ellers et validert oppslagsobjekt:

- Én kilde kan bare ha én `SourceOp`; ett eksklusivt mål kan bare ha én eier.
- En nøkkel kan ikke være både kilde og mål (heller ikke svakt mål). Mål
  under en kilde og kilder under et mål som opprettes, avvises.
- `Derive.from` kan ikke være et eksklusivt mål, og må ha samme identtype
  som `Derive.target`. Flere `Derive` fra samme eksisterende kilde er lov.

`claims.lookup(key)` velger den mest spesifikke kilderegelen ved å prøve hele
nøkkelen før foreldrene. `matchedSize` og `unmatchedSize` viser hvor mye av
nøkkelen som ble truffet. `requiredExistingTargets` viser svake mål som
ikke opprettes eller flyttes hit av reglene; det er **ikke et databaseoppslag**.
Denne valideringen sjekker verken om kildeentiteter finnes, om slike mål
finnes i alle relevante registre, ID-tildeling eller om en sink vil godta
resultatet. Eksisterende DTO-validering gjelder fremdeles når DTO-en kjøres.

## Bro til eksisterende kode

For regler som DTO-en kan uttrykke, bruk `toReguleringDTO` i servermodulen
før den vanlige lagrings- og kjøringsveien:

```kotlin
val claims = ReformClaims.validate(reform {
    move(gammelVeg, nyVeg)
}).fold(
    { feil -> error("Ugyldige regler: $feil") },
    { it },
)
val regulering = claims.toReguleringDTO(id, navn, dato, kommuneService).fold(
    { feil -> error("Kan ikke uttrykkes som regulering: $feil") },
    { it },
)
```

Her er `gammelVeg` og `nyVeg` vegidenter av samme type, og `kommuneService`
er det vanlige oppslaget. `toReguleringDTO` samler operasjonene i én
`EndringDTO`, henter fylke-/kommuneopplysninger ved behov og avviser
operasjoner som DTO-en ikke kan uttrykke. Den kontrollerer at
`regulering.toReguleringsinput().toReformOps()` gir **samme mengde
`ReformOp`-regler med like payloads**. Vil du i stedet undersøke en
eksisterende DTO, kan du lage reglene med
`regulering.toReguleringsinput().toReformOps()` og validere dem eksplisitt.
`toEndringDTO` alene gjør ikke rundturssjekken og bør ikke brukes som
garanti når endringen legges inn sammen med andre endringer.

En vellykket rundtur er *ikke* kjøreevivalens: `mapInput` og
`IdentTransformer` fortsetter å tolke DTO-en slik de gjorde før. For
eksempel kan ny ID, etterfølger, sammenslåingsflagg, tilordning av
etterkommere og teiger få ulik betydning i legacy-motoren og i
op-modellen. Ingen `ReformOp` kjøres direkte i denne leveransen.

DTO-en har også snevrere uttrykksevne: `Move` av fylke/kommune,
vilkårlig `Retire`, fri `Derive` og vilkårlige opprettelser blir
avvist hvis de ikke lar seg gjengi via DTO-ens endringstyper.
Gårdsnummerserier og veger som mål forutsettes å finnes; broen
oppretter dem ikke. Kommunedata normaliseres til reguleringens ene
ikrafttredelsesdato og navn i store bokstaver; opplysninger uten
tilsvarende felt i payloaden kan gå tapt, og op-rekkefølgen (dermed
mulig ID-tildelingsrekkefølge) er ikke bevart. Bruk derfor ikke broen
som et tapsfritt eksportformat for alle DTO-felter.

Dette er bare språk, strukturell validering og en avgrenset DTO-bro.
Direkte kjøring av ReformOp, nye SPI-er, data-/sink-forhåndsvalidering,
nytt HTTP-format og endret lagring eller tørrkjøring hører til en
separat innføring.
