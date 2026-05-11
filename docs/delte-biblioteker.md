# Biblioteker delt mellom Komreg og Matrikkelen

## Oversikt

Det er to delte biblioteker mellom komreg-backend og matrikkel:

- **core-api** – koden ligger i komreg-backend. Definerer det felles "språket" — domene-objekter (`Entity`, `Kommune`, `Matrikkelenhet`) og SPI-grensesnitt (`EntitySource`, `EntitySink`, `Ident`). Brukes av begge sider.

- **matrikkel-komreg** – koden ligger i matrikkel. Implementerer core-api sine grensesnitt med de faktiske sources og sinks som snakker med Matrikkelen.

Under kjøring fungerer det slik:

1. komreg-backend starter en kjøring
2. `EntitySource`-er (implementert i matrikkel-komreg) henter data fra Matrikkelen
3. komreg-backend omnummererer `Ident`-er på `Entity`-objektene
4. `EntitySink`-er (implementert i matrikkel-komreg) skriver de transformerte objektene tilbake

matrikkel-komreg lastes inn via Java ServiceLoader.

```
┌─────────────────────────┐          ┌─────────────────────────┐
│     komreg-backend      │          │       matrikkel         │
│                         │    GH    │                         │
│  ┌───────────┐GH Actions│ Packages │                         │
│  │ core-api  │──────────┼─────────►│  komreg/komreg.gradle   │
│  └───────────┘          │          │    (bruker core-api)    │
│                         │          │                         │
│                         │  GitHub  │  ┌─────────────────┐    │
│  server/               ◄┼──Packages── │ matrikkel-komreg│    │
│  build.gradle.kts       │ GH Actions  │  (:komreg)      │    │
│                         │          │  └─────────────────┘    │
└─────────────────────────┘          └─────────────────────────┘
```

## Publisering

| Bibliotek        | Publiseres til  | Jobb                                                                                                                                       |
|------------------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| core-api         | Github Packages | [Github Actions: *Publish core-api to GH-packages*](https://github.com/kartverket/komreg-backend/actions/workflows/publish-core-api.yml)   |
| matrikkel-komreg | GitHub Packages | [GitHub Actions: *Build and Publish Matrikkel Komreg*](https://github.com/kartverket/komreg-backend/actions/workflows/build-matrikkel.yml) |

Publiseringsflyten er:

- core-api utvikles og bygges i komreg-backend
- core-api-artefakten publiseres til Github Packages, slik at Matrikkelen kan bruke den
- matrikkel-komreg bygges i matrikkel med core-api som avhengighet
- matrikkel-komreg-artefakten publiseres til GitHub Packages
- server i komreg-backend bruker matrikkel-komreg

## Endringer i matrikkel-komreg

Hvis man gjør endringer i matrikkel-komreg, f.eks. ved å legge til eller endre en source eller sink:

1. Kjør GitHub Actions-workflowen *Build and Publish Matrikkel Komreg* (`build-matrikkel.yml`), som publiserer biblioteket til GitHub Packages.
2. Hvis Matrikkelen har bumpet versjon, oppdater versjonsnummeret i komreg-backend sin versjonskatalog:
   ```
   # gradle/libs.versions.toml
   matrikkel-komreg = { module = "no.kartverket.komreg:komreg-matrikkel", version = "..." }
   ```
   Avhengigheten brukes i `server/build.gradle.kts`.
3. Hvis versjonen er en SNAPSHOT-versjon, må man sannsynligvis tvinge byggingen til å refreshe dependencies:
   - **Lokalt:** Kjør Gradle med `--refresh-dependencies`, eller høyreklikk på komreg-backend i Gradle-fanen i IntelliJ og kjør "Refresh Gradle dependencies".
   - **GitHub Actions:** Slett den nyeste cachen som starter med `dependencies-` i [repo-caches](https://github.com/kartverket/komreg-backend/actions/caches).

## Endringer i core-api

Hvis man gjør endringer i core-api, f.eks. for å gjøre endringer på `Ident`: 

1. Kjør workflow publish-core-api.yml med nytt versjonsnummer.
2. Oppdater versjonsnummeret for avhengigheten i Matrikkelen, i `komreg/komreg.gradle`.
3. Gjør det som står i [Endringer i matrikkel-komreg](#endringer-i-matrikkel-komreg).

## Avhengigheter som må holdes i synk

Siden core-api, matrikkel-komreg og server alle ender opp på samme classpath i podene, må de bruke samme versjoner av delte biblioteker. Ellers får man runtime-feil. Komreg-backend burde følge versjonene som er i Matrikkelen.
Dette er disse vi veit om per nå, men det kan være flere som dukker opp.

### Arrow (io.arrow-kt)

Pinnet til 1.2.0 i komreg-backend (`gradle/libs.versions.toml`).
Matrikkelen henter Arrow transitivt via core-api.

Arrow er ignorert i `.github/dependabot.yml` 
