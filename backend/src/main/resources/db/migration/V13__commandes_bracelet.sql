-- Commandes signées envoyées aux bracelets et leurs accusés (REQ-MUST-07, US-SYS-011 ; FG-DOC-08 §8.1).

CREATE TABLE dispositifs.commande (
    id           UUID          NOT NULL PRIMARY KEY,
    bracelet_id  UUID          NOT NULL REFERENCES dispositifs.bracelet (id),
    type         VARCHAR(16)   NOT NULL CHECK (type IN ('MODE_ALERTE', 'CONFIGURATION', 'RETRAIT', 'LOCALISER')),
    -- Numéro strictement croissant par bracelet : le bracelet refuse tout numéro déjà vu (anti-rejeu).
    sequence     BIGINT        NOT NULL,
    -- Message signé tel qu'il est publié, conservé pour pouvoir le réémettre à l'identique.
    message      TEXT          NOT NULL,
    statut       VARCHAR(12)   NOT NULL CHECK (statut IN ('EMISE', 'ACCUSEE', 'REFUSEE', 'EXPIREE')),
    emise_le     TIMESTAMPTZ   NOT NULL,
    expire_le    TIMESTAMPTZ   NOT NULL,
    accusee_le   TIMESTAMPTZ,
    version      BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (bracelet_id, sequence)
);

CREATE INDEX commande_en_attente_idx ON dispositifs.commande (statut, expire_le) WHERE statut = 'EMISE';
