-- Safe Zones, suivi de présence et franchissements (REQ-MUST-10, 11 ; FG-DOC-07 tableau 6).

CREATE TABLE geolocalisation.safe_zone (
    id           UUID          NOT NULL PRIMARY KEY,
    enfant_id    UUID          NOT NULL,
    type         VARCHAR(12)   NOT NULL CHECK (type IN ('CERCLE', 'POLYGONE')),
    nom          VARCHAR(40)   NOT NULL,
    categorie    VARCHAR(12)   NOT NULL CHECK (categorie IN ('ECOLE', 'MAISON', 'FAMILLE', 'CULTE', 'AUTRE')),
    centre       GEOGRAPHY(Point, 4326),
    rayon_m      INTEGER       CHECK (rayon_m BETWEEN 50 AND 5000),
    polygone     GEOGRAPHY(Polygon, 4326),
    -- Jours (1 = lundi … 7 = dimanche) et heures locales : {"jours":[1,2,3,4,5],"debut":"07:00","fin":"17:30"}.
    plage        JSONB         NOT NULL,
    tolerance_s  INTEGER       NOT NULL CHECK (tolerance_s BETWEEN 0 AND 3600),
    statut       VARCHAR(12)   NOT NULL CHECK (statut IN ('ACTIVE', 'SUSPENDUE')),
    cree_le      TIMESTAMPTZ   NOT NULL,
    modifie_le   TIMESTAMPTZ   NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    CHECK ((type = 'CERCLE' AND centre IS NOT NULL AND rayon_m IS NOT NULL AND polygone IS NULL)
        OR (type = 'POLYGONE' AND polygone IS NOT NULL AND centre IS NULL AND rayon_m IS NULL))
);

CREATE INDEX safe_zone_enfant_idx ON geolocalisation.safe_zone (enfant_id);
CREATE INDEX safe_zone_centre_idx ON geolocalisation.safe_zone USING GIST (centre);
CREATE INDEX safe_zone_polygone_idx ON geolocalisation.safe_zone USING GIST (polygone);

-- État du suivi d'une zone pendant sa plage horaire : sert à n'émettre une sortie qu'après le délai de tolérance.
CREATE TABLE geolocalisation.suivi_zone (
    zone_id          UUID         NOT NULL PRIMARY KEY REFERENCES geolocalisation.safe_zone (id) ON DELETE CASCADE,
    vu_dedans        BOOLEAN      NOT NULL,
    dehors_depuis    TIMESTAMPTZ,
    sortie_signalee  BOOLEAN      NOT NULL,
    derniere_mesure  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE geolocalisation.franchissement (
    id          UUID                   NOT NULL PRIMARY KEY,
    zone_id     UUID                   NOT NULL REFERENCES geolocalisation.safe_zone (id) ON DELETE CASCADE,
    type        VARCHAR(8)             NOT NULL CHECK (type IN ('SORTIE', 'RETOUR')),
    point       GEOGRAPHY(Point, 4326) NOT NULL,
    mesure_le   TIMESTAMPTZ            NOT NULL,
    detecte_le  TIMESTAMPTZ            NOT NULL
);

CREATE INDEX franchissement_zone_idx ON geolocalisation.franchissement (zone_id, detecte_le DESC);
