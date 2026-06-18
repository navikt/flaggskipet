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

`mise.toml` setter ikke-sensitive defaults for host, port, databasenavn og brukernavn. Sett `FLAGGSKIPET_DB_PASSWORD` i `mise.local.toml`. Filen er git-ignorert og skal ikke committes.

Applikasjonen bruker `FLAGGSKIPET_DB_URL` fra NAIS når den finnes, og prefikser den med `jdbc:` for Hikari. Hvis ikke, bygges lokal URL fra `FLAGGSKIPET_DB_HOST`, `FLAGGSKIPET_DB_PORT` og `FLAGGSKIPET_DB_DATABASE` som settes av `mise.toml`.

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

- `mise run infra` — starter Docker Compose i bakgrunnen.
- `mise run infra:down` — stopper infrastruktur.
- `mise run infra:clean` — stopper og sletter volumer (ren lokal database).
- `mise tasks` — viser tilgjengelige kommandoer.

Flyway kjører ved appoppstart, og appen blir ready når migreringene er ferdige.
