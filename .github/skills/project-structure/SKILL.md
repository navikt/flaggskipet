---
name: project-structure
description: "Gir regler for mappestruktur i flaggskipet med DDD-grenser mellom api, bootstrap, domain, application og infrastructure. Brukes når bruker oppretter eller flytter features, use cases, Kafka, jobber eller eksterne klienter som ereg."
---

# Project structure

Bruk denne skillen når du skal plassere nye filer i prosjektet.
Målet er tydelige grenser: domenet skal ikke kjenne Ktor, Koin, database eller Kafka.

## Gjeldende struktur

```text
no.nav.flaggskipet
├── Application.kt
├── api
├── bootstrap
└── infrastructure
```

## Plasseringsregler

1. `api/` for HTTP-adaptere:
   - routes, request/response, API-feilkontrakt og Ktor plugins
2. `bootstrap/` for wiring:
   - Koin-oppsett, lifecycle, app-oppsett og routing-komposisjon
3. `infrastructure/` for teknologiadaptere:
   - `db`, `kafka`, eksterne klienter under `clients` (for eksempel `clients/ereg`)
4. `application/` for use cases og porter (når de innføres)
5. `domain/` for domenemodell og regler (når de innføres)

## Når du legger til nye features

- Lag feature-komposisjon i `api/<feature>/` eller `api/internal/`.
- Injiser avhengigheter i feature-komposisjon, ikke i global `Application.kt`.
- Hold `api/Routing.kt` tynn: kall feature-oppsett. Felles plugins installeres i `api/plugins`.

## Koin-moduler og DI-registrering

DI er komponert i `bootstrap/DependencyInjection.kt`. Hver modul samler avhengigheter for ett område.

Slik registrerer du en ny modul:

1. Lag en `xxxModule(...)`-funksjon som returnerer `Module`, plassert hos området den hører til
   (for eksempel `infrastructure/db/DatabaseModule.kt`, senere `infrastructure/kafka/KafkaModule.kt`).
2. Send inn avhengigheter som parametre og fang dem i closure — ikke registrer dem som `single` bare for å hente dem med `get()` igjen.
3. App-brede singletons uten teknologitilknytning hører hjemme i `bootstrap/CoreModule.kt` (`coreModule`).
4. Legg modulen til i `modules(...)`-listen i `installDependencyInjection`.

Eksempel:

```kotlin
modules(
    coreModule(applicationState),
    databaseModule(appConfig.database),
    // texasModule(appConfig.texas),
)
```

## Konfigurasjon

- Hver konfigurasjonsgruppe har en `XConfig.fromConfig(ApplicationConfig)` som leser og validerer (fail-fast med samlede feil).
- Les verdier med den delte `ApplicationConfig.stringOrEmpty(path)` i `infrastructure/config`.
- Samle alle gruppene i `AppConfig` (`infrastructure/config/AppConfig.kt`), bygget én gang i `Application.module()`.
- Map miljøvariabler til config-nøkler i `application.conf` (én `xxx { }`-blokk per gruppe).

## Kafka

- Legg producer/consumer, serializer og topic-adaptere i `infrastructure/kafka/`.
- Hold domenelogikk utenfor Kafka-klassene.
- Flytt orkestrering til `application/` når use cases kommer.

## Jobber (datapopulering, batch)

- Jobb-start og scheduling i `bootstrap/` (entrypoint/wiring).
- Jobbimplementasjon i `application/jobs/` når mappen finnes.
- Teknisk IO brukt av jobben (db, kafka, klienter) i `infrastructure/*`.

## Eksterne klienter (for eksempel ereg)

- Legg HTTP-klient i `infrastructure/clients/ereg/`.
- Bruk alltid `infrastructure/clients/<integrasjon>/` for nye eksterne klienter.
- Hold kontrakter/DTO-er nær klienten hvis de er rent tekniske.
- Mapping til domene flyttes til `application/` eller `domain/` når relevant.

## Referanser

Se [references/placement-map.md](references/placement-map.md) for konkret matrise.
