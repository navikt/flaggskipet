-- Vurderingsgrunner typesikres i Kotlin. Databasen skal ikke ha en global
-- verdiliste som binder fremtidige tiltakspakker til grunnene for pakke 1.
ALTER TABLE tiltakspakke_deltakelse
    DROP CONSTRAINT chk_tiltakspakke_vurderingsgrunn,
    ALTER COLUMN vurderingsgrunn TYPE TEXT USING vurderingsgrunn::TEXT;
