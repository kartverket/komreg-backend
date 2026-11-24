# Handoff 2: `IdTypeProvider` og egen Gradle-modul for matrikkel-komreg

**Anbefalt modell:** Claude Sonnet. **Reasoning:** medium.

`ReformOp.Create` har ingen kildeentitet, så generatoren vet ikke hvilken `IdType` den nye entiteten skal ha.
`Derive` arver `entity.id.type` fra kilden og trenger ikke dette. Løsningen er en valgfri SPI som pluginene kan
implementere. I dag finnes det bare én plugin (matrikkel-komreg). Implementasjonen legges derfor i dette repoet, i
en egen modul, og flyttes til matrikkel-komreg senere.

## 1. SPI i `lib/core-api`

Ny fil `lib/core-api/src/main/kotlin/no/kartverket/komreg/integration/spi/IdTypeProvider.kt`:

```kotlin
/**
 * Gir [IdType] for nye entiteter uten kilde ([ReformOp.Create]). Valgfri: en plugin uten provider kan ikke få
 * opprettet entiteter uten kilde.
 */
interface IdTypeProvider {
    /** `null` hvis provideren ikke kjenner [identType]. */
    suspend fun idTypeFor(identType: IdentOrEmptyType<*>): IdType<*, *>?
}
```

Den er bakoverkompatibel. Eksisterende plugins trenger ingen endring.

## 2. Ny modul som pakker inn matrikkel-komreg

I dag er `server` direkte avhengig av `libs.matrikkel.komreg` (`server/build.gradle.kts`, linje ~84). Kommentaren
der sier at den egentlig skulle vært `runtimeOnly`. `StedsnavnService` bruker `ssrJson` fra den.

Lag en ny modul, for eksempel `:plugins:matrikkel-komreg` i `plugins/matrikkel-komreg/`. Navnet kan velges fritt.

- `settings.gradle.kts`: legg til modulen.
- `plugins/matrikkel-komreg/build.gradle.kts`:

  ```kotlin
  plugins {
      id("komreg.kotlin-common")
      `java-library`
  }

  dependencies {
      api(project(":lib:core-api"))
      api(libs.matrikkel.komreg) {
          exclude(group = "com.oracle.database.jdbc")
      }
      // tester: samme oppsett som lib/transformation
  }
  ```

  `api` gjør at `server` fortsatt ser `ssrJson` og pluginklassene. Det holder ServiceLoader og `StedsnavnService`
  uendret. Å skjule matrikkel-komreg bak modulen, slik at `server` kan bruke `runtimeOnly`, er en egen oppgave og
  ikke en del av denne.
- `server/build.gradle.kts`: bytt `implementation(libs.matrikkel.komreg) { ... }` med
  `implementation(project(":plugins:matrikkel-komreg"))`. Flytt kommentaren med.
- Oppdater modulgrafen og modultabellen i [arkitektur.md](arkitektur.md).

## 3. Implementasjon for matrikkel-komreg

`plugins/matrikkel-komreg/src/main/kotlin/.../MatrikkelIdTypeProvider.kt`, registrert i
`src/main/resources/META-INF/services/no.kartverket.komreg.integration.spi.IdTypeProvider`.

Sammenlign med identtypene fra `no.statkart.matrikkel.komreg` (`getFylkeIdentType()`, `getKommuneIdentType()`
og så videre i `IdentTypes.kt`). De kommer fra den samme cachen, så `==` på identitet holder. Dette er beslutning 3.

| Identtype | `IdType` |
|---|---|
| `getFylkeIdentType()` | `Matrikkeltyper.Fylke` |
| `getKommuneIdentType()` | `Matrikkeltyper.Kommune` |
| alt annet | `null` |

I dag tillater domenereglene `Create` bare for fylke og kommune (`SinkCapabilities.kt`, `v1Capabilities`). Legg
ikke til flere typer før en sink støtter dem. Da feiler en ny `Create` tydelig i stedet for å skrive noe sinken
ikke kan håndtere.

## 4. Oppslag i `server`

Lag `IdTypeProviderManager` etter samme mønster som `EntitySinkManager` og `KommuneServiceManager`
(`server/.../integration/`):

```kotlin
class IdTypeProviderManager(private val providers: List<IdTypeProvider> = ServiceLoader.load(IdTypeProvider::class.java).toList()) {
    suspend fun idTypeFor(identType: IdentOrEmptyType<*>): IdType<*, *>? {
        val found = providers.mapNotNull { it.idTypeFor(identType) }.distinct()
        check(found.size <= 1) { "Flere IdType for $identType: $found" }
        return found.singleOrNull()
    }
}
```

`idTypeFor` sendes inn i `ReformTransformer` i [handoff-3](handoff-3-kjoring-fra-regler.md).

## Tester

- `MatrikkelIdTypeProvider`: fylke og kommune gir riktig `Matrikkeltyper`. Veg gir `null`.
- `IdTypeProviderManager`: ingen provider gir `null`. To providere med ulikt svar feiler.
- ServiceLoader finner provideren fra `server` (en enkel test i `server`, eller i den nye modulen).

## Ferdig når

- `./gradlew build` går grønt, og `server` starter som før.
- `StedsnavnService` og pluginene lastes fortsatt.
- Ingen kode i `lib/` er avhengig av matrikkel-komreg.
