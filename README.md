# KOMREG

## Modernisert kommuneendringssystem

# Innholdsfortegnelse

1. [Kom i gang](#kom-i-gang)

* [Lokalt oppsett](#lokalt-oppsett)
* [Miljøvariabler](#oppsett-av-miljøvariabler)
* [Tilgangsstyring](#tilgangsstyring)

# Kom i gang

Før man setter i gang med komreg, kan det være fint å lese gjennom flytdiagrammet for hele transformasjonsprosessen.
Denne finner du [her](https://miro.com/app/board/uXjVN68gwSc=/)
## Lokalt oppsett

| Fil                             | Path                                  |
|---------------------------------|---------------------------------------| 
| application main fil            | `./server/src/.../Application.kt`     |
| config-fil for database-oppsett | `.env`                                |
| http request eksempel           | `./http/local/runTransformation.http` |

Lag en `gradle.properties` fra en kopi av `gradle.properties.example`. 
Sett`GH_PACKAGES_PAT` til en PAT du har generert her: 
https://github.com/settings/tokens (du trenger bare `read:packages`).

Når man kjører opp løsningen lokalt trenger man å lage en kopi av `.env.template` på rot, og kalle den `.env`.
Hvis man vil kjøre mot en spesifikk database så endrer man bare på verdiene i denne. Den er ignorert i git, så
det er ingen fare for å sjekke den inn.

## Oppsett av miljøvariabler

| Variabel                        |
|---------------------------------|
| `DB_KOMREG_JDBC_URL`            | 
| `DB_MATRIKKEL_BACKING_USERNAME` | 
| `DB_MATRIKKEL_BACKING_PASSWORD` | 
| `DB_KOMREG_USERNAME`            | 
| `DB_KOMREG_PASSWORD`            |
|                                 |

Verdiene for disse variablene er påkrevd, og må eksistere i `.env`-filen din. Verdiene du skal sette for du tak i ved å
spørre Smia. I dev, test og prod-miljøene er variablene allerede satt på GCP.

### Førstegangsoppsett:

Du må ha en lokal postgres-instans kjørende. Dette kan gjøres med Docker-kommandoen under. Databasen inneholder tabeller
for komreg-domenet. Se [Skjemaspesifikasjon for komreg-db](#skjemaspesifikasjon-for-komreg-db) for en forklaring av de
ulike entitetene som eksiterer for komreg.

```bash
docker run --name local-postgres -e POSTGRES_USER=komreg -e POSTGRES_PASSWORD=passord -e POSTGRES_DB=komreg-db -p 54XX:5432 -d postgres
```
(Bytt ut XX så du bruker en ledig port på maskinen din.)

#### Legg til komreg-db som en datasource i IntelliJ hvis du vil:

Bruk URL `jdbc:postgresql://localhost:54XX/komreg-db`
Bruk porten du satte i run-kommandoen over.  
Fyll også inn inn brukernavn og passord i vinduet der du legger til Data Source.  
I schemas-fanen i samme vindu: velg å vise alle schemas i databasen.

#### Kjøring av lokal komreg-database:

1. Start Docker:
```bash
docker start
```

2. Se om du har containeren kjørende (local-postgres):
```bash
docker ps -a
```

3. Start containeren hvis den ikke kjører:
```bash
docker start local-postgres
```

Komreg-databasen skal nå være tilgjengelig.

#### Starte komreg-backend

Start komreg-backend ved å kjøre main metoden i `Application.kt` i `server`-modulen. Siden transformasjonsprosessen
krever ekstremt mye minne er det lurt å benytte seg av denne configen som du kan importere til Intellij. Denne allokerer
40Gb til transformasjonsprosessen.

```xml

<component name="ProjectRunConfigurationManager">
    <configuration default="false" name="Application" type="Application" factoryName="Application">
        <option name="ALTERNATIVE_JRE_PATH" value="17"/>
        <option name="ALTERNATIVE_JRE_PATH_ENABLED" value="true"/>
        <option name="MAIN_CLASS_NAME" value="no.kartverket.komreg.ApplicationKt"/>
        <module name="komreg-backend.server.main"/>
        <option name="VM_PARAMETERS" value="-Xmx40G"/>
        <method v="2">
            <option name="Make" enabled="true"/>
        </method>
    </configuration>
</component>
```

**MERK**: Første gangen du kjører applikasjonen kjører flyways migrasjonsfiler. Du vil du få en `RuntimeException` som
sier `Ingen ledige mottakerskjema`. Dette må løses annerledes i fremtiden, men er for øyeblikket en sikkerhetssperre mot
å skrive til et matrikkelskjema som er eksponert mot test-brukere eller prod-brukere. Gå i `mottakerskjema`i `komreg-db`
og sett et av skjemaene `isfree` til `true`. Vi eksponerer aldri ut matrikkel-db kopiene som komreg skriver til i dev,
så det er trygt å velge `MOTTAKER1`eller `MOTTAKER2` for lokal utvikling. For test- og prodmiljøene må du være helt
sikker på at skjemaet du velger er ledig.

### Skjemaspesifikasjon for komreg-db

| Skjema                 | Beskrivelse                                                                                                |
|------------------------|------------------------------------------------------------------------------------------------------------|
| `mottakerskjema`       | Mottakerskjema transformasjonsprosessen gjør endringer på.                                                 |
| `kjoring`              | Starttid, slutttid, status og skjema en regulering har blitt kjørt på.                                     |                                          
| `regulering`           | Endringer som skal gjøres på matrikkelen                                                                   |
| `tilbakeføringsstatus` | Status for hver sink transformasjonene er kjørt på.                                                        |
| `transformasjon `      | Identer transformasjonsmotoren har fått en unik match på fra reguleringen som skal endres i mottakerskjema |

### Feilhåndtering

Det er flere faktorer som kan gå galt under oppsett av prosjektet lokalt. En av de mest vanlige (og veldig irriterende)
feilene er at Gradle nekter å laste ned den siste versjonen av matrikkel-komreg pakken fra matrikkelen. Av og til løses
dette med en enkel oppfriskning av Gradle-avhengighetene, men andre ganger må man gjennom følgende punktliste for å få
bibliotekene i synk:

1. Høyreklikk på rotmappen i `komreg-backend` og velg "Open Module Settings".
2. Under "Project Settings" finner man fanen "Libraries". Trykk deg inn på denne.
3. Fjern biblioteket `Gradle: no.statkart.matrikkel:matrikkel-komreg:X.XX-SNAPSHOT` og trykk "Apply".
4. Frisk opp Gradle-avhengighetene og bygg på nytt. (En sjelden gang i blant kreves det omstart av IntelliJ før dette
   steget.)
5. Forhåpentligvis skal dette være i boks nå.

For å kjøre enkelte docker-images som er bygget for x86-arkitektur på Mac-er med ARM-brikker kan det være nødvendig med
en Colima-container. Dette kan man spinne opp på følgende måte.

1. Sett Colima som kontekst for Docker:

```bash
docker context use colima
```

2. Start Colima med x86-arkitektur (og gi den rikelig med minne):

```bash
colima start --memory 8 --arch x86_64
```
## Tilgangsstyring

Applikasjonen kjører på SKIP med tilgangsstyring vha. ztoperator. 
Dette gjør at kun autentiserte og autoriserte brukere kan gjøre kall mot dev- og prod-miljø.

### Autorisering

For å være autorisert til å hente accesstoken og gjøre kall mot komreg må du være medlem av en autorisert gruppe:
* aktv3-dev: AAD_TF_TEAM_SMIA
* atkv3-prod: AAD_KOMREG_PROD_USERS

Du kan sjekke om du er medlem ved å gå inn på EntraID og søke på gruppen under "Groups". 
Hvis du ikke er medlem og mener du skal være det i noen av disse gruppene, ta kontakt med Team SMIA.

### Autentisering

Gitt at du er autorisert må du bruke [az-cli](https://learn.microsoft.com/en-us/cli/azure/?view=azure-cli-latest) til å hente ut accesstoken. 
Følg dokumentasjonen til Microsoft for å finne ut hvordan du laster det ned for din maskin.

Når du har az-cli installert kan du starte med å logge inn:

```
az login --allow-no-subscriptions
```

Nå skal du være autentisert, og kan hente ut accesstoken fra en komreg-klientregistrering du er autorisert til å få token fra. 
Du finner klientregistreringen under "App registrations" i Entra. Søk etter "komreg". Så finner du CLIENT_ID på forsiden til klientregistreringen.

```
az account get-access-token --scope <CLIENT_ID>/.default
```

Hvis alt er som det skal fikk du et accesstoken i respons. 
Anbefaler å bruke postman til å enkelt sette opp kall mot komreg-backend med token.