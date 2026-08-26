-- Tiltakspakken er ikke lansert, og alle eksisterende vurderinger er
-- prelaunch-data som skal beregnes på nytt med korrigert regel.
--
-- TRUNCATE-låsen beholdes til transaksjonen er ferdig. Når defaulten fjernes i
-- samme transaksjon, kan gamle appinstanser ikke skrive ufullstendige rader
-- etter tømmingen; oppdatert kode setter alltid vurderingsgrunn eksplisitt.
TRUNCATE TABLE tiltakspakke_deltakelse;

ALTER TABLE tiltakspakke_deltakelse
    ALTER COLUMN vurderingsgrunn DROP DEFAULT;
