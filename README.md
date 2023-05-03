# KOMREG

## Modernisert kommuneendringssystem

## Lokalt oppsett

| Fil                                   | Path                                                  |
|---------------------------------------|-------------------------------------------------------|
| gradle eksempel fil for github access | `./gradle.properties.example`                         |
| application main fil                  | `./server/src/.../Application.kt`                     |
| config-fil for database-oppsett       | `./transformation/src/resources/reference-local.conf` |
| http request eksempel                 | `./http/local/runTransformation.http`                 |

Lag en `gradle.properties` fra en kopi av `gradle.properties.example`. Sett `GH_USERNAME`
til ditt Github-brukernavn og `GH_PACKAGES_PAT` til en PAT du har generert her:
https://github.com/settings/tokens (du trenger bare `read:packages`).

Under `transformation/src/resources/` finner man konfigurasjonsfilene for hvilken database
transformasjonene går mot. Når man kjører opp løsningen lokalt defaulter løsningen til `reference-local.conf`.
For å velge en annen database enn default lokalt docker-image overskriv verdiene i denne fila.

Start applikasjonen ved å kjøre funksjonen `Application.main()` som ligger i `server/src/.../Application.kt`.

For å starte transformeringen kjør http-requesten som ligger under `http/local/runTransformation.http`.
