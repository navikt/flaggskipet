# Flaggskipet

[![Build Status](https://github.com/navikt/flaggskipet/actions/workflows/build-and-deploy.yaml/badge.svg)](https://github.com/navikt/flaggskipet/actions/workflows/build-and-deploy.yaml)

[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Ktor](https://img.shields.io/badge/Ktor-087CFA?logo=ktor&logoColor=white)](https://ktor.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Gradle](https://img.shields.io/badge/Gradle-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)

Flaggskipet er en Ktor-backend for feature flags i team-esyfo.

## Formål

Dette repoet inneholder Ktor-applikasjonen, lokal PostgreSQL for utvikling og NAIS-oppsett for deploy til dev og prod.

## Database

Appen bruker PostgreSQL 18 via Cloud SQL i dev og lokal Postgres via Docker Compose.

## Autentisering

`POST /api/v1/tiltakspakker/vurdering` krever et TokenX-token med flaggskipet som audience.
Tokenet valideres via Texas-sidecaren. Appen krever i tillegg sikkerhetsnivået
`Level4`/`idporten-loa-high`. Endepunktene under `/internal/` er åpne for prober og
Prometheus-scraping.

Flaggskipet autentiserer hvem som kaller, men kontrollerer ikke at innlogget bruker
representerer organisasjonsnumrene i requesten. Flaggskipet har ingen egen kilde til
denne tilknytningen, så det ansvaret ligger hos konsumenten.

### Skaffe token for testing mot dev

1. Åpne [token-generatoren](https://tokenx-token-generator.intern.dev.nav.no/api/obo?aud=dev-gcp:team-esyfo:flaggskipet) (krever naisdevice).
2. Logg inn med en syntetisk testbruker fra ID-porten.
3. Bruk `access_token` fra svaret som Bearer-token mot `https://flaggskipet.intern.dev.nav.no`,
   for eksempel som `token`-variabel i dev-miljøet i [bruno](bruno).

Se [NAIS-dokumentasjonen](https://doc.nais.io/auth/tokenx/how-to/generate/) for flere varianter.

## Utvikling

Se [Utvikling](docs/development/utvikling.md) for lokal database og oppsett.
Bruk `mise tasks` for tilgjengelige kommandoer.

## Dokumentasjon

Dokumentasjon og beslutningsgrunnlag for tjenesten finnes i [docs](docs) mappen.
Se [pilotregel og klargjøring for tiltakspakke 1](docs/tiltakspakke1_pilot.md)
for geografisk scope, lagret vurderingsgrunnlag og opprydding før lansering.

## For Nav-ansatte

Spørsmål om tjenesten kan tas i [#esyfo på Slack](https://nav-it.slack.com/archives/C012X796B4L).
