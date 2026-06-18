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
- Hold `bootstrap/Routing.kt` tynn: installer felles plugins, kall feature-oppsett.

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
