-- Module identite : comptes, codes à usage unique, sessions et consentements (FG-DOC-07, tableau 11).

CREATE TABLE identite.utilisateur (
    id                  UUID         NOT NULL PRIMARY KEY,
    type                VARCHAR(16)  NOT NULL CHECK (type IN ('TUTEUR', 'AGENT')),
    -- Téléphone chiffré AES-256-GCM ; l'empreinte HMAC sert seule à la recherche exacte.
    telephone_chiffre   BYTEA,
    telephone_hash      VARCHAR(64) UNIQUE,
    statut              VARCHAR(24)  NOT NULL CHECK (statut IN ('EN_INSTRUCTION', 'ACTIF', 'SUSPENDU', 'CLOS')),
    mdp_argon2id        TEXT,
    mdp_modifie_le      TIMESTAMPTZ,
    echecs_connexion    INTEGER      NOT NULL DEFAULT 0,
    verrouille_jusqu_a  TIMESTAMPTZ,
    cree_le             TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT tuteur_avec_telephone CHECK (type <> 'TUTEUR' OR (telephone_chiffre IS NOT NULL AND telephone_hash IS NOT NULL))
);

CREATE TABLE identite.code_usage_unique (
    id          UUID         NOT NULL PRIMARY KEY,
    finalite    VARCHAR(32)  NOT NULL,
    cible_hash  VARCHAR(64)     NOT NULL,
    -- Seule l'empreinte du code est conservée.
    empreinte   BYTEA        NOT NULL,
    emis_le     TIMESTAMPTZ  NOT NULL,
    essais      INTEGER      NOT NULL DEFAULT 0,
    consomme    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX code_usage_unique_cible_idx ON identite.code_usage_unique (finalite, cible_hash, emis_le DESC);

CREATE TABLE identite.jeton_rafraichissement (
    id              UUID         NOT NULL PRIMARY KEY,
    utilisateur_id  UUID         NOT NULL REFERENCES identite.utilisateur (id),
    -- Tous les jetons issus d'une même connexion partagent une famille : la réutilisation d'un jeton
    -- déjà échangé révoque la famille entière.
    famille         UUID         NOT NULL,
    empreinte       VARCHAR(64)     NOT NULL UNIQUE,
    emis_le         TIMESTAMPTZ  NOT NULL,
    expire_le       TIMESTAMPTZ  NOT NULL,
    utilise_le      TIMESTAMPTZ,
    revoque_le      TIMESTAMPTZ
);

CREATE INDEX jeton_rafraichissement_famille_idx ON identite.jeton_rafraichissement (famille);
CREATE INDEX jeton_rafraichissement_utilisateur_idx ON identite.jeton_rafraichissement (utilisateur_id);

CREATE TABLE identite.consentement (
    id              UUID         NOT NULL PRIMARY KEY,
    utilisateur_id  UUID         NOT NULL REFERENCES identite.utilisateur (id),
    type            VARCHAR(32)  NOT NULL,
    accorde         BOOLEAN      NOT NULL,
    version_texte   VARCHAR(16)  NOT NULL,
    enregistre_le   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX consentement_utilisateur_idx ON identite.consentement (utilisateur_id, type, enregistre_le DESC);
