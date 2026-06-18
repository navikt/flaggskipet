# Utvikling

Dette er den lokale databasekontrakten:

| Variabel | Verdi | Kommentar |
| --- | --- | --- |
| `FLAGGSKIPET_DB_HOST` | `localhost` | Lokal PostgreSQL |
| `FLAGGSKIPET_DB_PORT` | `5432` | Standard PostgreSQL-port |
| `FLAGGSKIPET_DB_DATABASE` | `flaggskipet` | Databasenavn |
| `FLAGGSKIPET_DB_USERNAME` | `flaggskipet` | Databasebruker |
| `FLAGGSKIPET_DB_PASSWORD` | Sett i `mise.local.toml` | DB-passord skal ikke committes |

`mise.toml` setter ikke-sensitive defaults for host, port, databasenavn og brukernavn. Sett `FLAGGSKIPET_DB_PASSWORD` i `mise.local.toml`. Filen er git-ignorert og skal ikke committes.

Eksempel:

```toml
[env]
FLAGGSKIPET_DB_PASSWORD = "<sett ditt passord her>"
```

Lokal oppstart:

1. Kjør `mise install`.
2. Opprett `mise.local.toml`.
3. Start PostgreSQL med `docker compose up postgres`.
4. Bruk `mise tasks` for tilgjengelige kommandoer.

Flyway kjører ved appoppstart, og appen blir ready når migreringene er ferdige.
