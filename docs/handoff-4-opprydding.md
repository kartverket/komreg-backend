# Handoff 4: opprydding

**Anbefalt modell:** Claude Sonnet. **Reasoning:** lav–medium. Kompilatoren og testene fanger det meste. Bruk
medium for punktene merket *vurder*.

**Forutsetter** [handoff-3](handoff-3-kjoring-fra-regler.md). Fase A er nok for punkt 1. Resten krever fase B.

## 1. Fjern legacy-motoren

- Fjern motorvalget fra handoff-3 og gjør `reform` til eneste motor.
- Slett `IdentTransformer.kt` (med `Mapping`), `mapInput` og `map*`-funksjonene i `Transformer.kt`, og testene
  `IdentTransformer*Test`. De tilfellene som er portet i handoff-1, beholdes.
- Behold `ReformMapping.kt` (`toReformOps`) så lenge `server` tar imot DTO-er.

## 2. Domeneregler som speiler DTO-en

Disse reglene i `regulering-api/reform/.../rules/SinkCapabilities.kt` speiler begrensninger i DTO-en og
`mapInput`, ikke i sinkene. `VegTilbakeforingService` har ingen fylkesjekk og lager en kopi når `id != fraId`.

- `V1Kinds.ADDRESS_CODE`: fjern `deriveOnlyFromMove` og `targetsInOneCounty` fra `checks`. Oppdater `hints` og
  `defaultHint`, som i dag sier at en av delene må ha `role: identity`.
- Slett `deriveOnlyFromMove` og `DomainErr.DeriveWithoutMove` hvis de ikke brukes andre steder.
- *Vurder* `targetsInOneCounty` for `V1Kinds.MUNICIPALITY`. Les `KommuneSink` i matrikkel-komreg
  (`mottaker/`) og avgjør om sinken støtter kommunedeling på tvers av fylker. Fjern sjekken bare hvis den gjør det.

**Viktig:** så lenge `ReformSpecService` (`regulering-api/server/.../service/reform/ReformSpecService.kt`) kjører
broen (`toReguleringDTO`) som siste steg, stopper broen utskilling og vegdeling på tvers av fylker med
`IngenDtoEkvivalent`, selv om domenereglene er fjernet. Fjern broen fra kjeden når fase B er på plass, og fjern
`ReformSteg.BRO` hvis det ikke brukes lenger.

## 3. Når `server` ikke lenger tar imot DTO-er (etter fase B)

- Slett `ReguleringValidator` og `Reguleringsinput`.
- *Vurder* om `lib/regulering-dto` og `lib/regulering-mapping` kan slettes, eller om frontend og eksisterende data
  fortsatt trenger dem. Spør brukeren før du sletter moduler.

## 4. Oppfølging: gårdsnummerserie som utgår

`seriesRetireNeedsTitleMoves` krever minst én `Move` av et bruksnummer når en gårdsnummerserie utgår (`Retire`).
Bruksnumre uten egen regel får en delvis nøkkel, og da krasjer `MatrikkelenhetTilbakeforingService`. *Vurder* en
dataregel som krever en regel for **alle** bruksnumre under serien, eller anbefal `Move` av serien i hintet. Spør
brukeren før du lager en ny regel.

## 5. Dokumentasjon

Oppdater:

- [reform-op.md](reform-op.md): fjern "Ingen kode kjører `ReformOp` direkte" og avsnittet
  "Samme regler betyr ikke samme kjøring". Beskriv `ReformTransformer` og semantikktabellen fra handoff-1. Oppdater
  "Hva broen ikke kan uttrykke" eller fjern det.
- [arkitektur.md](arkitektur.md): kjøringsflyten, den nye modulen fra handoff-2 og transporten fra fase B.
- [reform-format.md](reform-format.md) og [reform-format-design.md](reform-format-design.md): valideringskjeden
  (broen) og kapabilitetene for veg.
- Slett handoff-filene (`handoff-*.md`) når alt er gjort.

## Ferdig når

- `./gradlew build` går grønt.
- `grep -r "IdentTransformer\|mapInput" --include=*.kt` gir ingen treff.
- En spec med utskilling av veg og en vegdeling på tvers av fylker validerer uten feil i `regulering-api`, og gir
  forventede transformasjoner i `server`.
