# Plan: Kafka-fundament og sykmelding-konsument

Planen dekker issue #7 og #9 samlet. Oppgave #8 holdes utenfor. #9 løses med en smal minimumstabell for Kafka-triggerdata og idempotens. En senere jobb får ansvar for beriking og videre tiltakspakke-data.

## Valgt retning

- Bruk plain Apache Kafka-klient, siden repoet er Ktor og ikke har Spring Kafka eller Rapids & Rivers.
- Legg Kafka-adaptere i `infrastructure/kafka`.
- Bygg Kafka-koden slik at felles deler kan gjenbrukes av senere konsumenter: config, properties, poll/commit-loop, lifecycle/shutdown, record-metadata, feilklassifisering og test/dev-sending.
- Hold sykmelding-spesifikk parsing, validering og lagring separat fra felles Kafka-infrastruktur.
- Hold parsing og lagring separat: parseren skal kun hente ut og validere nødvendige felt til en intern kommandomodell. Lagring og upsert skjer i egen handler/service/repository-del.
- Legg minimumstabell og repository i `infrastructure/db`, avgrenset til Kafka-triggerdata.
- Legg en separat teknisk invalid-tabell for permanente parse- og valideringsfeil, uten full payload og uten PII.
- Start consumer kontrollert etter Flyway/DI er klar, og stopp den kontrollert ved application shutdown.
- Commit Kafka-offset manuelt først etter vellykket DB-lagring.
- Lagre rå `fnr` som avklart minimumsdata for senere sykmeldt/virksomhet-oppslag, men logg aldri fnr eller full payload.

## Avklaringer og kontrakt

1. Topic-navn: `teamsykmelding.syfo-sendt-sykmelding`.
2. Stabil consumer group-id må settes.
3. Meldingsformat: JSON uten schema registry.
4. Kafka value parses som `SendtSykmeldingKafkaMessage`.
5. `SykmeldingRecord(offset, sykmeldingId, message)` behandles som intern wrapper rundt Kafka record metadata/value, ikke som primær JSON-kontrakt.
6. `SendtSykmeldingKafkaMessage` inneholder:
   - `sykmelding: ArbeidsgiverSykmelding`
   - `kafkaMetadata: KafkaMetadata`
   - `event: Event`
7. Parseren skal ikke bruke Jackson. Bruk Kotlinx serialization/`Json.parseToJsonElement` og hent kun feltene vi trenger.
8. Idempotensnøkkel: `kafkaMetadata.sykmeldingId`. Hvis `event.sykmeldingId` finnes og avviker fra `kafkaMetadata.sykmeldingId`, lagres meldingen som permanent invalid og gyldig trigger-rad upsertes ikke.
9. Feltnavn og format:
   - `fnr`: `message.kafkaMetadata.fnr`
   - `organisasjonsnummer`: `message.event.arbeidsgiver.orgnummer`
   - `juridiskOrgnummer`: `message.event.arbeidsgiver.juridiskOrgnummer` hvis relevant senere, men ikke i minimumstabellen nå
   - `periode`: `message.sykmelding.sykmeldingsperioder[*].fom/tom`
   - `syketilfelleStartDato`: finnes i kontrakten, men er ikke nødvendig i minimumstabellen hvis periode er nok
10. Malformed/permanent ugyldige meldinger lagres sanitert i egen teknisk invalid-tabell. Offset committes etter vellykket feillagring.
11. Tombstone/null-payload kan forekomme og ignoreres i første omgang. Offset committes uten DB-lagring.
12. Topic finnes i `teamsykmelding-kafka-topics`. Read-ACL for `team-esyfo/flaggskipet` dokumenteres som ekstern forutsetning og håndteres utenfor denne repo-PR-en.

## Fase 1: Kafka-fundament og lokal infra

Agent: Kokk

Skills: `/kafka-topic`, `/nais-manifest`, `/project-structure`, `/readme-update`

Filer/områder:

- `gradle/libs.versions.toml`: legg til Kafka client og Testcontainers Kafka.
- `build.gradle.kts`: legg til runtime/test dependencies.
- `docker-compose.kafka.yaml`: legg lokal Kafka i egen Compose-fil som passer eksisterende `mise infra`.
- `mise.toml`: legg til enkle tasker for Kafka/dev-meldinger ved behov.
- `nais/nais-dev.yaml`: legg til `spec.kafka.pool: nav-dev` og dokumenter topic/ACL-forutsetning.
- `src/main/resources/application.conf`: legg til `kafka.consumers.sykmelding { ... }` med env-mapping.
- `src/main/kotlin/no/nav/flaggskipet/infrastructure/kafka/KafkaConfig.kt`: fail-fast config.
- `src/main/kotlin/no/nav/flaggskipet/infrastructure/kafka/KafkaConsumerRunner.kt` eller tilsvarende felles consumer-runner: gjenbrukbar poll/commit/shutdown-struktur for senere konsumenter.
- `src/main/kotlin/no/nav/flaggskipet/infrastructure/kafka/KafkaPropertiesFactory.kt` eller tilsvarende: gjenbrukbar bygging av Kafka properties.
- `src/main/kotlin/no/nav/flaggskipet/infrastructure/config/AppConfig.kt`: inkluder KafkaConfig.

Akseptanse:

- Lokal Kafka starter med eksisterende infra-kommando sammen med resten av lokal infrastruktur.
- Appen har config for bootstrap servers, topic `teamsykmelding.syfo-sendt-sykmelding`, group-id og auto offset reset.
- Planen dokumenterer at Kafkarator read-ACL må gis til `team-esyfo/flaggskipet` utenfor denne repo-PR-en.
- `enable.auto.commit=false` brukes for consumer.
- Felles Kafka-infrastruktur er ikke hardkodet til sykmelding-topic, men tar topic/group/parser/handler som konfigurasjon eller avhengigheter.
- NAIS dev har Kafka pool.
- Ingen domenebehandling eller EREG-oppslag.

Faseport:

- Etter Fase 1 skal arbeidet pause.
- Bruker gjør gjennomgang, vurderer eventuell refaktor og kjører commit selv.
- Fase 2 starter ikke før bruker eksplisitt ber om videre arbeid.

## Fase 2: Minimumstabell og DB-adapter for triggerdata

Agent: Kokk

Skills: `/flyway-migration`, `/postgresql-review`, `/security-review`, `/project-structure`

Filer/områder:

- `src/main/resources/db/migration/V2__sykmelding_hendelse.sql`: nye smale tabeller for gyldige triggerdata og sanitert invalid-håndtering.
- `src/main/kotlin/no/nav/flaggskipet/infrastructure/db/SykmeldingHendelseTable.kt`
- `src/main/kotlin/no/nav/flaggskipet/infrastructure/db/SykmeldingHendelseRepository.kt`
- `src/main/kotlin/no/nav/flaggskipet/infrastructure/db/DatabaseModule.kt`: DI-registrering.
- Tester for migrering og repository-idempotens.

Tabellavgrensning:

- `sykmelding_id TEXT NOT NULL UNIQUE` som idempotensnøkkel basert på `kafkaMetadata.sykmeldingId`.
- `fnr TEXT NOT NULL` hvis kontrakten garanterer feltet, ellers nullable med eksplisitt håndtering.
- `organisasjonsnummer TEXT` nullable.
- `periode_fom DATE` nullable, beregnet som tidligste `fom` fra `sykmeldingsperioder`.
- `periode_tom DATE` nullable, beregnet som seneste `tom` fra `sykmeldingsperioder`.
- `created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL`.
- `updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL`.
- Ikke lagre full sykmeldingspayload.
- Rå `fnr` lagres fordi det er nødvendig minimumsdata for senere oppslag, men skal aldri logges.
- Manglende `event.arbeidsgiver` eller `orgnummer` er gyldig og lagres som `organisasjonsnummer = null`.
- Tom eller manglende `sykmeldingsperioder` er gyldig og lagres som `periode_fom = null` og `periode_tom = null`.
- Hvis `sykmeldingsperioder` finnes, men en periode har manglende eller ugyldig `fom`/`tom`, regnes meldingen som permanent invalid.
- Manglende eller blank `kafkaMetadata.fnr` er permanent invalid og lagres sanitert i invalid-tabellen.
- Ikke modeller tiltakspakke, tildeling, opt-out, EREG-data eller berikingsstatus her.
- Én rad per sykmelding, ikke én rad per sykmeldingsperiode.
- Ingen historikktabell i første omgang.

Teknisk invalid-tabell:

- Egen tabell, for eksempel `sykmelding_kafka_invalid_message`.
- Lagre Kafka topic/partition/offset, teknisk feilkode og `created_at`.
- Ikke lagre payload eller exception-/feilmeldingstekst i invalid-tabellen.
- Replay/feilsøking gjøres ved å lese meldingen fra Kafka via topic/partition/offset så lenge den finnes innen retention.
- Bruk egne feilkoder for for eksempel `MALFORMED_JSON`, `MISSING_SYKMELDING_ID`, `MISSING_FNR`, `MISMATCHED_SYKMELDING_ID` og `INVALID_PERIOD`.
- Tombstone/null-payload lagres ikke i invalid-tabellen i første omgang.
- Ikke lagre fnr, orgnummer eller full payload.
- Unik indeks på `(topic, partition, offset)` for idempotent feillagring.
- Invalid-lagring skal være idempotent, for eksempel `insert if not exists`, slik at commit-feil etter DB-insert ikke gjør meldingen til en poison pill.
- Offset committes først etter at invalid-raden er lagret.

Akseptanse:

- Duplikat `sykmelding_id` gir ikke ny rad, men oppdaterer eksisterende rad med siste minimumsverdier.
- Repository kan upserte minimumsdata atomisk.
- FNR/full payload logges ikke.
- Flyway og Exposed matcher.

Faseport:

- Etter Fase 2 skal arbeidet pause.
- Bruker gjør gjennomgang, vurderer eventuell refaktor og kjører commit selv.
- Fase 3 starter ikke før bruker eksplisitt ber om videre arbeid.

## Fase 3: Kafka-konsument og lifecycle

Agent: Kokk

Skills: `/kafka-topic`, `/security-review`, `/observability-setup`, `/project-structure`

Filer/områder:

- `src/main/kotlin/no/nav/flaggskipet/infrastructure/kafka/SykmeldingConsumer.kt`
- `src/main/kotlin/no/nav/flaggskipet/infrastructure/kafka/SykmeldingMessageParser.kt`
- `src/main/kotlin/no/nav/flaggskipet/infrastructure/kafka/SykmeldingMessageHandler.kt` eller tilsvarende: kobler parsed melding til repository-lagring.
- `src/main/kotlin/no/nav/flaggskipet/infrastructure/kafka/KafkaModule.kt`
- `src/main/kotlin/no/nav/flaggskipet/bootstrap/Lifecycle.kt` eller egen bootstrap-wiring for start/stop.
- `src/main/kotlin/no/nav/flaggskipet/bootstrap/DependencyInjection.kt`
- Unit- og integrasjonstester.

Akseptanse:

- Consumer leser `teamsykmelding.syfo-sendt-sykmelding` med stabil group-id.
- Consumeren gjenbruker felles Kafka-runner/properties/lifecycle fra Fase 1 og inneholder bare sykmelding-spesifikk parser- og handler-kobling.
- Parser henter bare idempotens-ID, `kafkaMetadata.fnr`, `event.arbeidsgiver.orgnummer` og periode fra `sykmelding.sykmeldingsperioder`.
- Parser bruker Kotlinx/Json tree-ekstraksjon eller små `@Serializable` DTO-er med egne parsere for dato/tid. Jackson skal ikke innføres bare for Kafka.
- Parser lagrer ikke data og kjenner ikke repository/DB.
- Handler/service tar parsed melding og utfører gyldig upsert eller invalid-lagring via repository.
- Permanent ugyldige meldinger lagres sanitert i teknisk invalid-tabell uten PII-logging.
- Tombstone/null-payload ignoreres og committes uten DB-lagring.
- DB-lagring skjer før offset commit.
- Ved DB-feil committes ikke offset, heller ikke for invalid-tabellen.
- Shutdown bruker wakeup/close eller tilsvarende kontrollert stopp.

Faseport:

- Etter Fase 3 skal arbeidet pause.
- Bruker gjør gjennomgang, vurderer eventuell refaktor og kjører commit selv.
- Fase 4 starter ikke før bruker eksplisitt ber om videre arbeid.

## Fase 4: Kafka testutviklingsoppsett

Agent: Kokk

Skills: `/kafka-topic`, `/readme-update`, `/security-review`

Filer/områder:

- `src/test/kotlin/no/nav/flaggskipet/infrastructure/kafka/SykmeldingMessageFixtures.kt`
- `src/test/kotlin/no/nav/flaggskipet/infrastructure/kafka/SykmeldingKafkaIntegrationTest.kt`
- Dev-helper, for eksempel `src/testFixtures` hvis valgt, eller en liten JVM main/test utility som kan publisere meldinger lokalt.
- `mise.toml`: tasker som `kafka:send-valid`, `kafka:send-invalid-missing-event-id`, `kafka:send-invalid-malformed`.
- `docs/development/utvikling.md` eller README: kort bruk.

Design:

- Lag én JSON message factory med tydelige varianter:
  - gyldig melding
  - duplikat event-id
  - manglende event-id
  - manglende orgnummer/periode
  - malformed JSON
  - tombstone/null-payload som ignoreres
- Bruk syntetisk fnr, for eksempel `00000000000`, aldri ekte fnr.
- Hold verktøyet lett: ingen UI og ingen stort CLI-rammeverk.
- Dev-sender kjøres via enkle `mise kafka:send-*`-tasker og gjenbruker samme fixtures/message factory som testene.
- Dev-sender dokumenterer lokal Kafka-forutsetning og bruker bootstrap servers fra lokal config/env.
- Dev-sender/message factory utformes slik at nye Kafka-konsumenter kan legge til egne meldingsvarianter uten å kopiere producer-oppsettet.

Akseptanse:

- Utvikler kan sende både riktige og feil meldinger med enkle kommandoer.
- Testene verifiserer commit etter lagring, ikke commit ved DB-feil, og idempotent upsert ved duplikat.
- Fixtures kan gjenbrukes mellom unit-test, integrasjonstest og lokal dev-sending.

Faseport:

- Etter Fase 4 skal arbeidet pause.
- Bruker gjør gjennomgang, vurderer eventuell refaktor og kjører commit selv.
- Fase 5 starter ikke før bruker eksplisitt ber om videre arbeid.

## Fase 5: Verifisering og inspeksjon

Agent: Kokk + inspektører

Skills: `/security-review`, `/postgresql-review`, `/kafka-topic`

Kontroller:

- `./gradlew test`
- `./gradlew ktlintCheck`
- `./gradlew build`
- Lokal `mise infra` med Postgres + Kafka hvis Docker er tilgjengelig.
- Manuell dev-send av minst én gyldig og én ugyldig melding.

Inspeksjon:

- R4 krever kryssmodell-inspeksjon.
- Ekstra fokus på PII-logging, offset/idempotens, lifecycle/shutdown, Flyway-schema og NAIS Kafka-konfig.

Faseport:

- Etter Fase 5 skal arbeidet pause.
- Bruker gjør sluttgjennomgang, vurderer eventuell refaktor og kjører commit selv.
- Eventuell PR/opprydding starter ikke før bruker eksplisitt ber om videre arbeid.

## PR-strategi

Anbefalt: Én PR som dekker #7 og #9 samlet, fordi testoppsett, config, lokal Kafka og consumer henger tett sammen. PR-beskrivelse bør bruke `Closes #7` og `Closes #9` hvis begge akseptansekriterier fullføres.

Alternativ: To PR-er hvis man vil lande infra raskt:

1. #7 + testutviklingsoppsettets grunnmur.
2. #9 minimumstabell + consumer + integrasjonstester.

## Ikke inkludert

- #8-datamodell/repositories.
- EREG-oppslag.
- Tiltakspakke-tildeling.
- Full payload-lagring.
- DLQ som komplett produksjonsflyt, med mindre ugyldig-melding-strategien eksplisitt avklares dit.
