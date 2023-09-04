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


### Førstegangsoppsett av lokal transformasjonsdatabase:

1. Kopier `DB_KOMREG_JDBC_URL`, `DB_KOMREG_USERNAME` og `DB_KOMREG_PASSWORD` fra .env.template
til din egen .env-fil.

2. Start Docker eller Colima (antar Colima):
```bash
colima start
```

3. Kjør:
```bash
docker run --name local-postgres -e POSTGRES_USER=komreg -e POSTGRES_PASSWORD=passord -e POSTGRES_DB=local-komreg-db -p 54XX:5432 -d postgres
```
(Bytt ut XX så du bruker en ledig port på maskinen din.)

### Legg til local-tr-db som en datasource i IntelliJ hvis du vil:
Bruk url `jdbc:postgresql://localhost:54XX/local-komreg-db`  
Bruk porten du satte i run-kommandoen over.  
Fyll også inn inn brukernavn og passord i vinduet der du legger til Data Source.  
I schemas-fanen i samme vindu: velg å vise alle schemas i databasen.


### Kjøring av lokal transformasjonsdatabase:

1. Start Docker eller Colima (antar Colima):
```bash
colima start
```

2. Se om du har containeren kjørende (local-postgres):
```bash
docker ps -a
```

3. Start containeren hvis den ikke kjører:
```bash
docker start local-postgres
```

Transformasjonsdatabasen skal nå være tilgjengelig.