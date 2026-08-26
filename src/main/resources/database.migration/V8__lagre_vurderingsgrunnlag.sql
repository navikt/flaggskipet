ALTER TABLE tiltakspakke_deltakelse
    ADD COLUMN fylkeskode VARCHAR(2),
    -- Beholdes gjennom rullerende deploy slik at eldre appinstanser fortsatt kan skrive.
    ADD COLUMN vurderingsgrunn VARCHAR(32) NOT NULL DEFAULT 'IKKE_REGISTRERT';

ALTER TABLE tiltakspakke_deltakelse
    ADD CONSTRAINT chk_tiltakspakke_fylkeskode
        CHECK (fylkeskode IS NULL OR fylkeskode ~ '^[0-9]{2}$'),
    ADD CONSTRAINT chk_tiltakspakke_vurderingsgrunn
        CHECK (vurderingsgrunn IN (
            'FYLKE_I_SCOPE',
            'FYLKE_UTENFOR_SCOPE',
            'MANGLER_ADRESSE',
            'UGYLDIG_KOMMUNENUMMER',
            'UTGATT_FYLKESKODE',
            'IKKE_REGISTRERT'
        ));
