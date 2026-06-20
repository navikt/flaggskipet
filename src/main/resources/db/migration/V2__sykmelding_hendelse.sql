CREATE TABLE sykmelding_hendelse (
    sykmelding_id TEXT PRIMARY KEY,
    fnr TEXT NOT NULL,
    organisasjonsnummer TEXT NULL,
    periode_fom DATE NULL,
    periode_tom DATE NULL,
    event_timestamp TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE invalid_sykmelding_hendelse (
    topic TEXT NOT NULL,
    partition INTEGER NOT NULL,
    record_offset BIGINT NOT NULL,
    error_code TEXT NOT NULL,
    sykmelding_id TEXT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    CONSTRAINT invalid_sykmelding_hendelse_pk PRIMARY KEY (topic, partition, record_offset)
);
