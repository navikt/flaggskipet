# Plasseringsmatrise

Bruk tabellen som fasit når du er i tvil om mappe.

| Hva bygges | Hvor | Hvorfor |
|---|---|---|
| Route/endepunkt | `api/...` | HTTP-adapter |
| StatusPages / ApiError | `api/error` | API-feilkontrakt |
| CallId / ContentNegotiation | `api/plugins` | Felles Ktor-pluginer |
| Routing-komposisjon | `bootstrap` | Wiring-lag |
| Koin-moduler | `bootstrap` | Composition root |
| Lifecycle hooks | `bootstrap` | Runtime-oppsett |
| DB config, health, migrering | `infrastructure/db` | Teknologiadapter |
| Kafka consumer/producer | `infrastructure/kafka` | Teknologiadapter |
| Ereg-klient | `infrastructure/clients/ereg` | Ekstern integrasjon |
| Jobb-definisjon (batch) | `application/jobs` | Use case-orkestrering |
| Jobb-bootstrap (start/schedule) | `bootstrap` | Oppstart/wiring |
| Domeneentitet/regler | `domain` | Uavhengig av teknologi |

## Tommelfingerregel

Hvis klassen trenger Ktor, Koin, JDBC, Kafka-API eller HTTP-klientbibliotek, hører den normalt ikke hjemme i `domain`.
