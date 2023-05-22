# KOMREG

## Modernisert kommuneendringssystem

## Lokalt oppsett

| Fil                                   | Path                                  |
|---------------------------------------|---------------------------------------|
| gradle eksempel fil for github access | `./gradle.properties.example`         |
| application main fil                  | `./server/src/.../Application.kt`     |
| config-fil for database-oppsett       | `.env`                                |
| http request eksempel                 | `./http/local/runTransformation.http` |

Lag en `gradle.properties` fra en kopi av `gradle.properties.example`. Sett `GH_USERNAME`
til ditt Github-brukernavn og `GH_PACKAGES_PAT` til en PAT du har generert her:
https://github.com/settings/tokens (du trenger bare `read:packages`).

I `transformation/src/resources/properties.conf` finner man konfigurasjonen for hvilken database
transformasjonene går mot, som henter inn nødvendige properties fra miljøvariabler i det gitte miljøet.
Når man kjører opp løsningen lokalt trenger man å lage en kopi av `.env.template` på rot, og kalle den `.env`. 
Hvis man vil kjøre mot en spesifikk database så endrer man bare på verdiene i denne. Den er ignorert i git, så 
det er ingen fare for å sjekke den inn.

Start applikasjonen ved å kjøre funksjonen `Application.main()` som ligger i `server/src/.../Application.kt`.

For å starte transformeringen kjør http-requesten som ligger under `http/local/runTransformation.http`.
