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

## Utvikling

Se [Utvikling](docs/development/utvikling.md) for lokal database og oppsett.
Bruk `mise tasks` for tilgjengelige kommandoer.

## Dokumentasjon

Dokumentasjon og beslutningsgrunnlag for tjenesten finnes i [docs](docs) mappen.

## For Nav-ansatte

Spørsmål om tjenesten kan tas i [#esyfo på Slack](https://nav-it.slack.com/archives/C012X796B4L).
