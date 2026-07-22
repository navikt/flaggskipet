ALTER TABLE tiltakspakke_deltakelse
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

DROP INDEX idx_tiltakspakke_deltakelse_orgnr_tiltakspakke;
DROP INDEX idx_tiltakspakke_deltakelse_tiltakspakke_id;
