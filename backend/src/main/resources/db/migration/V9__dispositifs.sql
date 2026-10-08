-- Bracelets, configuration et appairages (REQ-MUST-07, 08, 20 ; FG-DOC-07 tableau 5).

CREATE TABLE dispositifs.bracelet (
    id                        UUID         NOT NULL PRIMARY KEY,
    -- Numéro gravé (FG-2291) : identifiant d'appareil, nom commun du certificat et seul identifiant
    -- affiché sur la page publique. Distinct de l'IMEI, qui est chiffré.
    numero_serie              VARCHAR(16)  NOT NULL UNIQUE,
    imei_chiffre              BYTEA        NOT NULL,
    imei_empreinte            VARCHAR(64)  NOT NULL UNIQUE,
    revision_materielle       VARCHAR(16)  NOT NULL,
    statut                    VARCHAR(16)  NOT NULL
        CHECK (statut IN ('EN_STOCK', 'ACTIF', 'PERDU', 'VOLE', 'EN_SAV', 'REFORME')),
    version_logiciel          VARCHAR(16)  NOT NULL,
    empreinte_certificat      VARCHAR(64)  NOT NULL UNIQUE,
    certificat_revoque_le     TIMESTAMPTZ,
    -- Empreinte SHA-256 du jeton du QR gravé ; le jeton n'est remis qu'une fois, à l'enregistrement.
    jeton_qr_sha256           VARCHAR(64)  NOT NULL UNIQUE,
    -- Empreinte à clé du code d'appairage imprimé sur la carte d'activation ; vide une fois le code utilisé.
    code_appairage_empreinte  VARCHAR(64)  UNIQUE,
    garantie_jusqu_au         DATE,
    -- Après une déclaration de perte, le suivi continue jusqu'à cette date pour aider à retrouver le bracelet.
    suivi_jusqu_au            TIMESTAMPTZ,
    cree_le                   TIMESTAMPTZ  NOT NULL,
    modifie_le                TIMESTAMPTZ  NOT NULL,
    version                   BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX bracelet_statut_idx ON dispositifs.bracelet (statut);

CREATE TABLE dispositifs.configuration (
    bracelet_id           UUID     NOT NULL PRIMARY KEY REFERENCES dispositifs.bracelet (id),
    intervalle_normal_s   INTEGER  NOT NULL CHECK (intervalle_normal_s BETWEEN 30 AND 3600),
    intervalle_alerte_s   INTEGER  NOT NULL CHECK (intervalle_alerte_s BETWEEN 5 AND 600),
    intervalle_economie_s INTEGER  NOT NULL CHECK (intervalle_economie_s BETWEEN 60 AND 7200),
    mode_economie         BOOLEAN  NOT NULL
);

CREATE TABLE dispositifs.appairage (
    id           UUID         NOT NULL PRIMARY KEY,
    bracelet_id  UUID         NOT NULL REFERENCES dispositifs.bracelet (id),
    enfant_id    UUID         NOT NULL,
    debut        TIMESTAMPTZ  NOT NULL,
    fin          TIMESTAMPTZ,
    motif_fin    VARCHAR(16)  CHECK (motif_fin IN ('DESAPPAIRAGE', 'PERTE', 'VOL', 'PANNE', 'REMPLACEMENT')),
    CHECK ((fin IS NULL) = (motif_fin IS NULL))
);

-- Un seul appairage actif par bracelet et par enfant.
CREATE UNIQUE INDEX appairage_actif_bracelet_idx ON dispositifs.appairage (bracelet_id) WHERE fin IS NULL;
CREATE UNIQUE INDEX appairage_actif_enfant_idx ON dispositifs.appairage (enfant_id) WHERE fin IS NULL;
CREATE INDEX appairage_enfant_idx ON dispositifs.appairage (enfant_id, debut DESC);
