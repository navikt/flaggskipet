-- Metadata om hver tiltakspakke for analyse. Kan joines med
-- tiltakspakke_deltakelse på tiltakspakke_id. Må holdes i takt med
-- Tiltakspakker.kt; endringer gjøres med nye migreringer.
CREATE TABLE tiltakspakke
(
    id        TEXT PRIMARY KEY,
    startdato DATE,
    sluttdato DATE,
    arver_fra TEXT
);

INSERT INTO tiltakspakke (id, startdato, sluttdato, arver_fra)
VALUES ('OPPFOLGINGSPLAN_TILTAKSPAKKE_1', DATE '2026-09-16', DATE '2026-11-25', NULL),
       ('DIALOGMOTE_TILTAKSPAKKE_2', NULL, NULL, 'OPPFOLGINGSPLAN_TILTAKSPAKKE_1');
