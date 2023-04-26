# KOMREG

## Modernisert kommuneendringssystem

### Kjør lokalt

Kjør opp database lokalt med Docker.

Lag en `gradle.properties` fra en kopi av `gradle.properties.example`. Sett `GH_USERNAME`
til ditt Github-brukernavn og `GH_PACKAGES_PAT` til en PAT du har generert her:
https://github.com/settings/tokens

Kjør `Application.main()` – defaulter til `local` settings, som peker på lokal database.
