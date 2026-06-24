CREATE TABLE tiltakspakke (
    id TEXT PRIMARY KEY,
    navn TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    slutt_dato DATE NULL
);

CREATE TABLE tiltakspakke_deltakelse (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tiltakspakke_id TEXT NOT NULL,
    orgnummer TEXT NOT NULL,
    deltakelse TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    CONSTRAINT fk_tiltakspakke FOREIGN KEY (tiltakspakke_id) REFERENCES tiltakspakke(id),
    CONSTRAINT uq_tiltakspakke_orgnr UNIQUE (tiltakspakke_id, orgnummer),
    CONSTRAINT chk_tiltakspakke_deltakelse CHECK (deltakelse IN ('DELTAR', 'DELTAR_IKKE', 'UTENFOR_SCOPE'))
);

CREATE INDEX idx_tiltakspakke_deltakelse_tiltakspakke_id ON tiltakspakke_deltakelse (tiltakspakke_id);
CREATE INDEX idx_tiltakspakke_deltakelse_orgnr ON tiltakspakke_deltakelse (orgnummer);
