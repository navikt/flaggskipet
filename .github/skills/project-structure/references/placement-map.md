# Plasseringsmatrise

Bruk tabellen som fasit når du er i tvil om mappe.

| Hva bygges | Hvor | Hvorfor |
|---|---|---|
| Route/endepunkt | `api/...` | HTTP-adapter |
| StatusPages / ApiError | `api/error` | API-feilkontrakt |
| CallId / ContentNegotiation | `api/plugins` | Felles Ktor-pluginer |
| Routing-komposisjon | `api/Routing.kt` | HTTP-adapter |
| Koin-moduler | `bootstrap` (composition root) + `xxxModule` hos området | Composition root samler, modulene eier egne avhengigheter |
| Lifecycle hooks | `bootstrap` | Runtime-oppsett |
| DB config, health, migrering | `infrastructure/db` | Teknologiadapter |
| Exposed-tabell (DSL `object FooTable : Table()`) | `infrastructure/db` | DB-skjema, ikke domene |
| Repository (`transaction(database) { ... }`, `ResultRow → domene`) | `infrastructure/db` | Persistensadapter |
| API-DTO + `Foo.toResponse()` (kun når wire avviker fra domene) | `api/<feature>` | HTTP-kontrakt |
| Kafka consumer/producer | `infrastructure/kafka` | Teknologiadapter |
| Ereg-klient | `infrastructure/clients/ereg` | Ekstern integrasjon |
| Jobb-definisjon (batch) | `application/jobs` | Use case-orkestrering |
| Jobb-bootstrap (start/schedule) | `bootstrap` | Oppstart/wiring |
| Domeneentitet/regler | `domain` | Uavhengig av teknologi |

## Tommelfingerregel

Hvis klassen trenger Ktor, Koin, JDBC, Kafka-API eller HTTP-klientbibliotek, hører den normalt ikke hjemme i `domain`.
