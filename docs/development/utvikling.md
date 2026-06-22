# Utvikling

Dette er den lokale databasekontrakten:

| Variabel | Verdi | Kommentar |
| --- | --- | --- |
| `FLAGGSKIPET_DB_HOST` | `localhost` | Lokal PostgreSQL |
| `FLAGGSKIPET_DB_PORT` | `5432` | Standard PostgreSQL-port |
| `FLAGGSKIPET_DB_DATABASE` | `flaggskipet` | Databasenavn |
| `FLAGGSKIPET_DB_USERNAME` | `flaggskipet` | Databasebruker |
| `FLAGGSKIPET_DB_PASSWORD` | Sett i `mise.local.toml` | DB-passord skal ikke committes |
| `FLAGGSKIPET_DB_URL` | Ikke satt lokalt | Settes av NAIS i cluster (full database-URL med credentials) |
| `FLAGGSKIPET_DB_SSLKEY_PK8` | Ikke satt lokalt | Settes av NAIS i cluster (sti til klientnøkkel i PKCS#8/DER-format) |

`mise.toml` setter ikke-sensitive defaults for host, port, databasenavn og brukernavn. Sett `FLAGGSKIPET_DB_PASSWORD` i `mise.local.toml`. Filen er git-ignorert og skal ikke committes.

Applikasjonen bruker `FLAGGSKIPET_DB_URL` fra NAIS når den finnes, og konverterer den til en JDBC-URL for Hikari uten credentials i selve URL-en. NAIS-URL-en peker `sslkey` på PEM-nøkkelen, men JDBC-driveren trenger PKCS#8/DER-nøkkelen, så `sslkey` overstyres med `FLAGGSKIPET_DB_SSLKEY_PK8`. Hvis URL-en ikke er satt, bygges lokal URL fra `FLAGGSKIPET_DB_HOST`, `FLAGGSKIPET_DB_PORT` og `FLAGGSKIPET_DB_DATABASE` som settes av `mise.toml`.

Eksempel:

```toml
[env]
FLAGGSKIPET_DB_PASSWORD = "<sett ditt passord her>"
```

Lokal oppstart:

1. Kjør `mise install`.
2. Opprett `mise.local.toml`.
3. Start lokal infrastruktur med `mise run infra`.
4. Start appen med `mise run dev`.

Mise-kommandoer for infrastruktur:

- `mise run infra` — starter Docker Compose i bakgrunnen og oppretter lokal Kafka-topic for sykmelding hvis den mangler.
- `mise run infra:down` — stopper infrastruktur.
- `mise run infra:clean` — stopper og sletter volumer (ren lokal database).
- `mise tasks` — viser tilgjengelige kommandoer.

Flyway kjører ved appoppstart, og appen blir ready når migreringene er ferdige.

Lokal Kafka-kontrakt settes også av `mise.toml`:

| Variabel | Verdi | Kommentar |
| --- | --- | --- |
| `FLAGGSKIPET_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Lokal Kafka via Docker Compose |
| `FLAGGSKIPET_KAFKA_SYKMELDING_ENABLED` | `true` | Setter om sykmelding-consumeren skal starte lokalt og i NAIS. |
| `FLAGGSKIPET_KAFKA_SYKMELDING_TOPIC` | `teamsykmelding.syfo-sendt-sykmelding` | Topic for sykmelding-consumer. Opprettes automatisk av `mise run infra` lokalt. |
| `FLAGGSKIPET_KAFKA_SYKMELDING_GROUP_ID` | `flaggskipet-sykmelding-v1` | Stabil consumer group-id for sykmelding-consumer |
| `FLAGGSKIPET_KAFKA_SYKMELDING_AUTO_OFFSET_RESET` | `earliest` | Lokal standard for sykmelding-consumer |

I NAIS settes `KAFKA_BROKERS`, `KAFKA_TRUSTSTORE_PATH`, `KAFKA_KEYSTORE_PATH` og `KAFKA_CREDSTORE_PASSWORD` automatisk når `kafka.pool` er aktivert. `mise run infra` starter alltid PostgreSQL fra `docker-compose.yaml` og Kafka fra `docker-compose.kafka.yaml`.

For å sende syntetiske sykmelding-meldinger lokalt:

- `mise run kafka:sykmelding` — sender en gyldig melding
- `mise run kafka:sykmelding valid` — sender en gyldig melding
- `mise run kafka:sykmelding invalid` — sender en permanent ugyldig melding
- `mise run kafka:sykmelding tombstone` — sender tombstone/null-payload
