-- Partage temporaire de la position avec un contact d'urgence (US-SEC-001).

CREATE TABLE geolocalisation.partage_position (
    id                    UUID         NOT NULL PRIMARY KEY,
    enfant_id             UUID         NOT NULL,
    cree_par              UUID         NOT NULL,
    -- Lien du contact avec l'enfant (« Oncle »), et son numéro : chiffré, affiché masqué.
    destinataire_lien     VARCHAR(40)  NOT NULL,
    destinataire_chiffre  BYTEA        NOT NULL,
    destinataire_masque   VARCHAR(24)  NOT NULL,
    -- Seule l'empreinte du jeton du lien est conservée : la base ne permet pas de reconstituer un lien.
    jeton_sha256          VARCHAR(64)  NOT NULL UNIQUE,
    debut                 TIMESTAMPTZ  NOT NULL,
    fin                   TIMESTAMPTZ  NOT NULL,
    revoque_le            TIMESTAMPTZ,
    ouvertures            INTEGER      NOT NULL DEFAULT 0,
    derniere_ouverture    TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 0,
    CHECK (fin > debut)
);

CREATE INDEX partage_position_enfant_idx ON geolocalisation.partage_position (enfant_id, fin DESC);
