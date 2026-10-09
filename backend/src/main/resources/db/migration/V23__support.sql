-- Support (US-PAR-017, US-SUP-001) : demandes des parents et base de connaissances.

CREATE SEQUENCE identite.reference_demande_support START 1;

CREATE TABLE identite.demande_support (
    id           UUID          NOT NULL PRIMARY KEY,
    reference    VARCHAR(16)   NOT NULL UNIQUE,
    tuteur_id    UUID          NOT NULL REFERENCES identite.utilisateur (id),
    objet        VARCHAR(120)  NOT NULL,
    statut       VARCHAR(20)   NOT NULL CHECK (statut IN ('OUVERTE', 'EN_ATTENTE_PARENT', 'RESOLUE')),
    ouverte_le   TIMESTAMPTZ   NOT NULL,
    modifiee_le  TIMESTAMPTZ   NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX demande_support_tuteur_idx ON identite.demande_support (tuteur_id, modifiee_le DESC);
CREATE INDEX demande_support_statut_idx ON identite.demande_support (statut, modifiee_le);

CREATE TABLE identite.message_support (
    id          UUID           NOT NULL PRIMARY KEY,
    demande_id  UUID           NOT NULL REFERENCES identite.demande_support (id) ON DELETE CASCADE,
    auteur      VARCHAR(8)     NOT NULL CHECK (auteur IN ('PARENT', 'SUPPORT')),
    auteur_id   UUID           NOT NULL,
    texte       VARCHAR(2000)  NOT NULL,
    cree_le     TIMESTAMPTZ    NOT NULL
);

CREATE INDEX message_support_demande_idx ON identite.message_support (demande_id, cree_le);

CREATE TABLE identite.article_aide (
    id          UUID           NOT NULL PRIMARY KEY,
    -- Segment d'adresse stable de l'article : /aide/sangle-qui-s-ouvre.
    slug        VARCHAR(80)    NOT NULL UNIQUE,
    categorie   VARCHAR(16)    NOT NULL CHECK (categorie IN ('BRACELET', 'ALERTES', 'SAFE_ZONES', 'PAIEMENT', 'COMPTE', 'VIE_PRIVEE')),
    titre       VARCHAR(120)   NOT NULL,
    -- Texte simple, paragraphes séparés par une ligne vide : jamais de balisage interprété.
    contenu     VARCHAR(6000)  NOT NULL,
    statut      VARCHAR(10)    NOT NULL CHECK (statut IN ('BROUILLON', 'PUBLIE')),
    lectures    BIGINT         NOT NULL DEFAULT 0,
    auteur_id   UUID           NOT NULL,
    modifie_le  TIMESTAMPTZ    NOT NULL,
    publie_le   TIMESTAMPTZ,
    version     BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX article_aide_publies_idx ON identite.article_aide (categorie, lectures DESC) WHERE statut = 'PUBLIE';
