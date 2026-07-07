CREATE TABLE tiltakspakke_deltakelse
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tiltakspakke_id TEXT                           NOT NULL,
    orgnummer       TEXT                           NOT NULL,
    deltakelse      TEXT                           NOT NULL,
    created_at      TIMESTAMPTZ      DEFAULT NOW() NOT NULL,
    updated_at      TIMESTAMPTZ      DEFAULT NOW() NOT NULL,
    CONSTRAINT uq_tiltakspakke_orgnr UNIQUE (tiltakspakke_id, orgnummer),
    CONSTRAINT chk_tiltakspakke_deltakelse CHECK (deltakelse IN ('TILTAKSGRUPPE', 'KONTROLLGRUPPE', 'UTENFOR_SCOPE'))
);

CREATE INDEX idx_tiltakspakke_deltakelse_tiltakspakke_id ON tiltakspakke_deltakelse (tiltakspakke_id);
CREATE INDEX idx_tiltakspakke_deltakelse_orgnr_tiltakspakke ON tiltakspakke_deltakelse (orgnummer, tiltakspakke_id);
