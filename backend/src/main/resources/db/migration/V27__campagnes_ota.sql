-- Mise à jour à distance du logiciel embarqué (US-PAR-013, REQ-MUST-07 ; FG-DOC-08 §7) : campagnes par vagues,
-- image acceptée seulement si son manifeste est signé par la clé de publication du logiciel.

ALTER TABLE dispositifs.commande
    DROP CONSTRAINT commande_type_check,
    ADD CONSTRAINT commande_type_check CHECK (type IN ('MODE_ALERTE', 'CONFIGURATION', 'RETRAIT', 'LOCALISER', 'MISE_A_JOUR'));

CREATE TABLE dispositifs.campagne_ota (
    id              UUID          NOT NULL PRIMARY KEY,
    version         VARCHAR(16)   NOT NULL UNIQUE,
    url_image       VARCHAR(300)  NOT NULL,
    taille_octets   BIGINT        NOT NULL CHECK (taille_octets > 0),
    sha256          VARCHAR(64)   NOT NULL,
    -- Signature ECDSA P-256 du manifeste « FG-OTA|version|taille|sha256 », vérifiée ici puis par chaque bracelet.
    signature       VARCHAR(128)  NOT NULL,
    note            VARCHAR(200),
    statut          VARCHAR(12)   NOT NULL CHECK (statut IN ('PREPAREE', 'EN_COURS', 'EN_PAUSE', 'TERMINEE')),
    -- Dernière vague lancée : 0 avant la première, 4 quand tout le parc est visé.
    vague_courante  INTEGER       NOT NULL DEFAULT 0 CHECK (vague_courante BETWEEN 0 AND 4),
    creee_par       UUID          NOT NULL,
    creee_le        TIMESTAMPTZ   NOT NULL
);

CREATE TABLE dispositifs.cible_ota (
    campagne_id   UUID         NOT NULL REFERENCES dispositifs.campagne_ota (id),
    bracelet_id   UUID         NOT NULL REFERENCES dispositifs.bracelet (id),
    vague         INTEGER      NOT NULL CHECK (vague BETWEEN 1 AND 4),
    emise_le      TIMESTAMPTZ  NOT NULL,
    installee_le  TIMESTAMPTZ,
    PRIMARY KEY (campagne_id, bracelet_id)
);

CREATE INDEX cible_ota_en_attente_idx ON dispositifs.cible_ota (bracelet_id) WHERE installee_le IS NULL;
