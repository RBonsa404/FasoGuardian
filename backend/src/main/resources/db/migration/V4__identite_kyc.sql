-- Vérification KYC du lien de filiation ou de tutelle (REQ-SYS-013 ; FG-DOC-07, tableau 3).

CREATE SEQUENCE identite.dossier_kyc_reference_seq START WITH 20001;

CREATE TABLE identite.dossier_kyc (
    id                 UUID         NOT NULL PRIMARY KEY,
    reference          VARCHAR(16)  NOT NULL UNIQUE,
    demandeur_id       UUID         NOT NULL REFERENCES identite.utilisateur (id),
    agent_id           UUID         REFERENCES identite.utilisateur (id),
    statut             VARCHAR(24)  NOT NULL CHECK (statut IN
                           ('BROUILLON', 'DEPOSE', 'EN_INSTRUCTION', 'COMPLEMENT_DEMANDE', 'APPROUVE', 'REJETE')),
    canal              VARCHAR(24)  NOT NULL CHECK (canal IN ('EN_LIGNE', 'POINT_INSCRIPTION')),
    nature_lien        VARCHAR(16)  NOT NULL CHECK (nature_lien IN ('PARENT', 'TUTEUR')),
    -- Identité du demandeur et de l'enfant déclaré : JSON chiffré AES-256-GCM (catégorie PIECE_KYC).
    identite_chiffree  BYTEA        NOT NULL,
    enfant_id          UUID         NOT NULL,
    enfant_chiffre     BYTEA        NOT NULL,
    -- Motif d'un rejet ou d'une demande de complément, rédigé par l'agent sans donnée d'identité.
    motif              VARCHAR(500),
    cree_le            TIMESTAMPTZ  NOT NULL,
    depose_le          TIMESTAMPTZ,
    decide_le          TIMESTAMPTZ,
    version            BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX dossier_kyc_statut_idx ON identite.dossier_kyc (statut, depose_le);
CREATE INDEX dossier_kyc_demandeur_idx ON identite.dossier_kyc (demandeur_id);

CREATE TABLE identite.piece_justificative (
    id               UUID         NOT NULL PRIMARY KEY,
    dossier_id       UUID         NOT NULL REFERENCES identite.dossier_kyc (id),
    type             VARCHAR(24)  NOT NULL CHECK (type IN
                         ('PIECE_RECTO', 'PIECE_VERSO', 'ACTE_NAISSANCE', 'JUGEMENT_TUTELLE')),
    contenu_chiffre  BYTEA        NOT NULL,
    type_mime        VARCHAR(32)  NOT NULL,
    -- Empreinte du contenu en clair, pour en vérifier l'intégrité après déchiffrement.
    sha256           VARCHAR(64)  NOT NULL,
    taille_octets    INTEGER      NOT NULL,
    deposee_le       TIMESTAMPTZ  NOT NULL,
    UNIQUE (dossier_id, type)
);

CREATE TABLE identite.lien_tutelle (
    id         UUID         NOT NULL PRIMARY KEY,
    tuteur_id  UUID         NOT NULL REFERENCES identite.utilisateur (id),
    enfant_id  UUID         NOT NULL,
    nature     VARCHAR(16)  NOT NULL CHECK (nature IN ('PARENT', 'TUTEUR')),
    statut     VARCHAR(16)  NOT NULL CHECK (statut IN ('ACTIF', 'SUSPENDU')),
    cree_le    TIMESTAMPTZ  NOT NULL,
    UNIQUE (tuteur_id, enfant_id)
);

CREATE INDEX lien_tutelle_enfant_idx ON identite.lien_tutelle (enfant_id);
