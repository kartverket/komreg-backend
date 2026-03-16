# M22 oppsett for testing av KomReg

For oppsett av selve komreg-backend, se [README](README.md).

Denne guiden beskriver hvordan man setter opp en lokal M22-instans med mock-data,
slik at man kan starte M22-klienten og se hva som skjer ved KomReg-kjøring.

> **Merk:** Mock-dataen kommer fra matrikkel-teamet. Generering av egen mock-data er mulig,
> men er en stor jobb. Bruk det eksisterende imaget med mindre du har spesifikke behov.

## Forutsetninger

- Tilgang til `nexus.statkart.no` docker registry
- Matrikkel-repoet klonet lokalt
- Docker kjørende

## 1. Konfigurer matrikkel-repoet

I `matrikkel.properties` (kommenter vekk andre verdier om nødvendig):

```properties
matrikkel.db.username=MATRIKKEL_KOMREG
```

## 2. Start M22-database

```bash
docker run --name matrikkel-db -p 1521:1521 -p 5500:5500 -d nexus.statkart.no:8082/matrikkel-oracle-xe-onedb:latest
```

Alternativt kan du bruke `./lokal-dev/start-db.sh` som starter både matrikkel-db og komreg-db.

## 3. Konfigurer `.env` for M22-testing

Kopier `.env.template` til `.env` (se [README](../README.md)) og sett følgende verdier:

```env
DB_MATRIKKEL_JDBC_URL=jdbc:oracle:thin:@//localhost:1521/devmatr1
DB_MATRIKKEL_BACKING_USERNAME=MATRIKKEL_KOMREG
DB_MATRIKKEL_BACKING_PASSWORD=MATRIKKEL_KOMREG
DB_MATRIKKEL_MOTTAKER1_USERNAME=MATRIKKEL_KOMREG
DB_MATRIKKEL_MOTTAKER1_PASSWORD=MATRIKKEL_KOMREG
DB_MATRIKKEL_MOTTAKER2_USERNAME=MATRIKKEL_KOMREG
DB_MATRIKKEL_MOTTAKER2_PASSWORD=MATRIKKEL_KOMREG
DB_MATRIKKEL_SYSTEM_USERNAME=SYSTEM
DB_MATRIKKEL_SYSTEM_PASSWORD=Matrikkeladm4kv
TOGGLE_SINK_OFF=false
LOG_APPENDER=STDOUT
DB_KOMREG_JDBC_URL=jdbc:postgresql://localhost:5432/komreg-db
DB_KOMREG_USERNAME=komreg
DB_KOMREG_PASSWORD=passord
```

> Hovedforskjellen fra template: brukernavn/passord settes til `MATRIKKEL_KOMREG` i stedet for
> `MATRIKKEL_DEV`, system-brukeren settes til `SYSTEM`/`Matrikkeladm4kv`, og `TOGGLE_SINK_OFF`
> settes til `false` slik at sink faktisk kjører.

## 4. Sett opp schema

Kjør fra matrikkel-repoet:

```bash
./gradlew --no-configure-on-demand --no-parallel flyway_core_init
./gradlew --no-configure-on-demand --no-parallel flyway_core_migrate
```

## 5. Kjør M22-klienten

For visuell testing:

```bash
./gradlew :client:runClientSingleVM
```

Eller i IntelliJ: quick run gradle `:client:runClientSingleVM`

## 6. Lagre database-snapshot

Siden hver KomReg-kjøring trenger fersk data, er det nyttig å kunne resette databasen.
Lag et Docker-image av databasen etter init/migrate:

```bash
docker stop matrikkel-db
docker commit -m "Lagt til testdata" matrikkel-db matrikkel-test-db
```

For å resette til dette snapshottet:

```bash
docker rm matrikkel-db
docker run --name matrikkel-db -p 1521:1521 -p 5500:5500 -d matrikkel-test-db:latest
```

Alternativt kan du bruke `./lokal-dev/db-snapshot.sh save` og `./lokal-dev/db-snapshot.sh restore` som gjør det samme.

## Tips: Sjekk endringslogg før/etter KomReg-kjøring

Før du kjører KomReg, noter høyeste id i `endring`-tabellen i M22-basen.
Etter kjøring kan du se KomReg sine endringer fra og med den id-en.

## Test med en Grensejustering

Lag en parameterfil og legg den til i komreg postgres-basen (`reguleringsinput`).
Filen skal beskrive en grensejustering der matrikkelenhet `100000201-2/90` flyttes
til kommune `100000106` (Fredrikstad).

Etter kjøring kan man verifisere endringene i M22-klienten og i endringsloggen.

## Gradle-oppsett

Nyttig for store Gradle-prosjekter. Legg i `~/.gradle/gradle.properties`:

```properties
org.gradle.parallel=true
org.gradle.workers.max=6
org.gradle.configuration.on.demand=true
org.gradle.warning.mode=all
```
