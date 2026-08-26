# Tiltakspakke 1: pilotregel og klargjøring

Dette dokumentet beskriver produksjonskontrakten for
`OPPFOLGINGSPLAN_TILTAKSPAKKE_1` og kontrollene som skal gjennomføres før
tiltakspakken tas i bruk av konsumentene.

## Produksjonskontrakt

- Piloten omfatter fylkeskode `55` (Troms) og `56` (Finnmark).
- Fylkeskode `50` (Trøndelag) er utenfor piloten.
- Fylkeskode `54` (tidligere Troms og Finnmark) er utgått og behandles som
  utenfor scope. Den lagres med vurderingsgrunnen `UTGATT_FYLKESKODE`, slik at
  utdatert EREG-data blir synlig.
- Regelen er lik i dev og prod. Utdaterte data i EREG Q2 skal ikke kompenseres
  med et dev-unntak som kan påvirke produksjon.
- Hver virksomhet i scope har 50 prosent sannsynlighet for å havne i
  tiltaksgruppen. Den faktiske populasjonen forventes derfor å bli omtrent
  50/50 fordelt mellom tiltaks- og kontrollgruppe. Tildelingen er deterministisk
  basert på tiltakspakke-ID og orgnummer, så samme virksomhet får samme resultat
  ved ny vurdering.

Den tidligere implementasjonen brukte et nytt tilfeldig tall ved hver ny
vurdering. Det ga en statistisk riktig 50/50-fordeling, men en virksomhet kunne
bytte gruppe etter sletting, retry før lagring eller ny populering. Den nye
implementasjonen hasher teksten `<tiltakspakke-ID>:<orgnummer>` med SHA-256 og
leser de første 48 bitene som et positivt heltall. Ved 50 prosent sannsynlighet
går den nederste halvparten av de mulige verdiene til tiltaksgruppen. Dette gir
fortsatt en pseudotilfeldig tildeling med 50 prosent sannsynlighet per
virksomhet, men tildelingen kan reproduseres og etterprøves. Algoritmen og
nøkkelformatet er en del av kontrakten for tiltakspakke 1 og skal ikke endres
etter lansering.

Tiltakspakke 1 skal ikke endre betydning etter lansering. En senere endring i
geografi, sannsynlighet eller tildelingsalgoritme skal opprettes som en ny
tiltakspakke-ID. Det er derfor ikke innført et eget konsept for regelversjon.
Tiltakspakke-ID som versjonsgrense avgjør ikke om samme virksomhet kan delta i
flere forskjellige tiltakspakker.

## Åpen beslutning: overlapp mellom tiltakspakker

Det finnes bare én produksjonspakke nå. Denne rettelsen endrer derfor ikke den
eksisterende flerpakkeatferden. Før en ny tiltakspakke gjøres gjeldende, må det
besluttes eksplisitt om virksomheter i tiltaks- eller kontrollgruppen for pakke
1 kan delta i den nye pakken, hvor lenge en eventuell sperre varer, og om
virksomheter som bare er vurdert `UTENFOR_SCOPE` skal omfattes.

Eksklusivitet skal ikke utledes indirekte av at det finnes en vilkårlig rad for
orgnummeret. Den vedtatte regelen må modelleres og testes eksplisitt før flere
pakker er aktive samtidig.

## Lagret vurderingsgrunnlag

For hver vurdering lagres fylkeskoden som ble utledet fra kommunenummeret og én
av disse vurderingsgrunnene:

- `FYLKE_I_SCOPE`
- `FYLKE_UTENFOR_SCOPE`
- `MANGLER_ADRESSE`
- `UGYLDIG_KOMMUNENUMMER`
- `UTGATT_FYLKESKODE`
- `IKKE_REGISTRERT`

`MANGLER_ADRESSE` betyr at vurderingslaget ikke mottok noen adresse fra EREG.
Det omfatter i dagens klient også HTTP 404, og er ikke en påstand om at en
registrert virksomhet nødvendigvis finnes uten adresse.

`IKKE_REGISTRERT` beskriver bare den midlertidige standardverdien som V8 bruker
før V10 nullstiller tabellen og fjerner standarden. Det skal ikke finnes slike
rader etter at V10 er kjørt. Nye vurderinger fra oppdatert kode har alltid et
konkret grunnlag. Hele EREG-adressen lagres ikke.

Databasen lagrer vurderingsgrunn som fri `TEXT` uten en global verdiliste.
Applikasjonen mapper teksten til den typesikre Kotlin-enumen `Vurderingsgrunn`.
Dermed krever en ny grunn en eksplisitt kodeendring og tester, men ikke en
skjemamigrering som også binder alle andre tiltakspakker til grunnene for pakke
1. En ukjent databaseverdi avvises ved lesing i stedet for å bli tolket som en
annen grunn. Kode som kjenner en ny enumverdi må derfor være ferdig utrullet før
verdien skrives.

Den første lagrede vurderingen for kombinasjonen tiltakspakke-ID og orgnummer
er autoritativ. Senere og samtidige forsøk overskriver den ikke; de leser og
returnerer vurderingen som allerede ligger i databasen.

En lagret vurdering er et endelig øyeblikksbilde for tiltakspakke 1. Dette
gjelder også `MANGLER_ADRESSE`, `UGYLDIG_KOMMUNENUMMER` og
`UTGATT_FYLKESKODE`; de prøves ikke automatisk på nytt. Valget hindrer at
senere adresseendringer eller skiftende EREG-data flytter en virksomhet inn og
ut av forsøket. Før lansering skal antallet slike datakvalitetsutfall vurderes.
Hvis et utfall skyldes feil eller midlertidige testdata, må den konkrete raden
slettes og vurderes på nytt før tiltakspakken aktiveres.

## Automatisk nullstilling ved deploy

Tiltakspakken er ikke lansert, tabellen inneholder bare prelaunch-data, og det
finnes ingen andre tiltakspakker med data som skal bevares. V10 gjør derfor
oppryddingen automatisk når denne versjonen deployes:

1. Alle rader i `tiltakspakke_deltakelse` slettes med `TRUNCATE`.
2. Standardverdien for `vurderingsgrunn` fjernes. Oppdatert kode skriver alltid
   et konkret grunnlag.

Begge operasjonene kjøres i samme Flyway-transaksjon. `TRUNCATE` holder en
eksklusiv tabellås til transaksjonen er ferdig. En write fra gammel kode som
fullføres før låsen, blir dermed slettet. En write fra gammel kode etter
nullstillingen avvises fordi den ikke setter det obligatoriske
`vurderingsgrunn`-feltet. Gammel kode kan derfor ikke fylle inn feil data på
nytt under en rullerende deploy.

Det er ikke nødvendig å stoppe Oppfølgingsplan, åpne en SQL-sesjon eller kjøre
en egen runbook. Kall som treffer en gammel appinstans i det korte
utrullingsvinduet, kan feile i stedet for å lagre en gammel vurdering. Dette er
akseptabelt før lansering fordi Oppfølgingsplan fortsatt forkaster svaret. Når
alle instanser er oppdatert, vil videre kall populere tabellen med korrekt
geografi og fullstendig vurderingsgrunnlag. Hvis prepopuleringen er en avgrenset
engangskjøring, må den spilles av på nytt etter deploy.

En rollback til gammel kode etter V10 vil gi skrivefeil fordi defaulten er
fjernet. Rettelsen skal derfor håndteres med fix-forward. Eksisterende korrekte
rader kan fortsatt leses av gammel kode.

Før tiltakspakken aktiveres hos konsumenten skal det bekreftes at V10 er kjørt,
at alle Flaggskipet-instansene er oppdatert, og at prepopuleringskallene lykkes
etter utrullingen.

## Manuell akseptansetest

Bruk tre kjente testvirksomheter som EREG oppgir med kommunenummer i henholdsvis
Troms (`55xx`), Finnmark (`56xx`) og Trøndelag (`50xx`).

Kall `POST /api/v1/tiltakspakker/vurdering` og kontroller at:

- `55xx` gir `TILTAKSGRUPPE` eller `KONTROLLGRUPPE`, aldri `UTENFOR_SCOPE`.
- `56xx` gir `TILTAKSGRUPPE` eller `KONTROLLGRUPPE`, aldri `UTENFOR_SCOPE`.
- `50xx` gir `UTENFOR_SCOPE`.
- Gjentatte kall returnerer samme lagrede tildeling.

Automatiserte golden-tester verifiserer i tillegg at samme orgnummer får samme
tildeling etter en full ny beregning, uten at akseptansetesten trenger direkte
databasetilgang.

Hvis EREG Q2 bare tilbyr gammel fylkeskode `54`, må testdataene i Q2 oppdateres
eller testen gjennomføres med kontrollerte lokale EREG-data. Produksjonsregelen
skal ikke utvides med `54` av den grunn.

Tiltakspakken skal ikke aktiveres hos konsumentene før V10-nullstillingen,
akseptansetesten og en oppdatert kontroll av analysegrunnlaget er dokumentert.
