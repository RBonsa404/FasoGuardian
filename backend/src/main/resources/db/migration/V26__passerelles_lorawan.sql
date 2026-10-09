-- Passerelles LoRaWAN autour d'écoles partenaires (US-SYS-004, REQ-COULD-06) : une trame entendue par une
-- passerelle confirme la présence du bracelet dans l'enceinte, sans réseau cellulaire.

ALTER TABLE telemetrie.position
    DROP CONSTRAINT position_source_check,
    ADD CONSTRAINT position_source_check CHECK (source IN ('GNSS', 'CELLULE', 'WIFI', 'LORA'));

CREATE TABLE telemetrie.passerelle (
    id             UUID                   NOT NULL PRIMARY KEY,
    -- Identifiant matériel de la passerelle (EUI-64, 16 chiffres hexadécimaux).
    eui            VARCHAR(16)            NOT NULL UNIQUE CHECK (eui ~ '^[0-9A-F]{16}$'),
    etablissement  VARCHAR(80)            NOT NULL,
    -- Centre et rayon de l'enceinte couverte : c'est la position attribuée au bracelet entendu.
    centre         GEOGRAPHY(Point, 4326) NOT NULL,
    rayon_m        INTEGER                NOT NULL CHECK (rayon_m BETWEEN 20 AND 2000),
    creee_le       TIMESTAMPTZ            NOT NULL,
    vue_le         TIMESTAMPTZ,
    retiree_le     TIMESTAMPTZ
);
