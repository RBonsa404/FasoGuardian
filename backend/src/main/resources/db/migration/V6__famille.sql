-- Module famille : fiche enfant, fiche santé, contacts d'urgence (FG-DOC-07, tableau 4).

CREATE TABLE famille.enfant (
    id              UUID         NOT NULL PRIMARY KEY,
    prenom          VARCHAR(80)  NOT NULL,
    nom             VARCHAR(80)  NOT NULL,
    date_naissance  DATE         NOT NULL,
    photo_ref       VARCHAR(128),
    cree_le         TIMESTAMPTZ  NOT NULL,
    modifie_le      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0
);

-- Historique des modifications de la fiche : qui, quand, quel champ. Les valeurs n'y sont pas recopiées.
CREATE TABLE famille.enfant_revision (
    id          UUID         NOT NULL PRIMARY KEY,
    enfant_id   UUID         NOT NULL REFERENCES famille.enfant (id),
    auteur_id   UUID         NOT NULL,
    champ       VARCHAR(32)  NOT NULL,
    modifie_le  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX enfant_revision_enfant_idx ON famille.enfant_revision (enfant_id, modifie_le DESC);

-- Fiche santé : contenu entièrement chiffré AES-256-GCM (catégorie SANTE), groupe sanguin compris.
CREATE TABLE famille.fiche_sante (
    enfant_id        UUID         NOT NULL PRIMARY KEY REFERENCES famille.enfant (id),
    contenu_chiffre  BYTEA        NOT NULL,
    modifie_le       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0
);

-- Journal des modifications de la fiche santé, non modifiable : aucune donnée médicale, seulement des décomptes.
CREATE TABLE famille.revision_sante (
    id                 UUID         NOT NULL PRIMARY KEY,
    enfant_id          UUID         NOT NULL REFERENCES famille.enfant (id),
    auteur_id          UUID         NOT NULL,
    nombre_elements    INTEGER      NOT NULL,
    nombre_critiques   INTEGER      NOT NULL,
    modifie_le         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX revision_sante_enfant_idx ON famille.revision_sante (enfant_id, modifie_le DESC);

CREATE FUNCTION famille.refuser_modification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Le journal des révisions de santé est en ajout seul';
END;
$$;

CREATE TRIGGER revision_sante_ajout_seul
    BEFORE UPDATE OR DELETE ON famille.revision_sante
    FOR EACH ROW EXECUTE FUNCTION famille.refuser_modification();

CREATE TABLE famille.contact_urgence (
    id                 UUID         NOT NULL PRIMARY KEY,
    enfant_id          UUID         NOT NULL REFERENCES famille.enfant (id),
    -- Lien avec l'enfant (« Tante », « Voisin »), seul élément affichable sur la page publique.
    lien               VARCHAR(40)  NOT NULL,
    nom_chiffre        BYTEA        NOT NULL,
    telephone_chiffre  BYTEA        NOT NULL,
    visible_sur_qr     BOOLEAN      NOT NULL DEFAULT FALSE,
    rang               INTEGER      NOT NULL,
    cree_le            TIMESTAMPTZ  NOT NULL
);

CREATE INDEX contact_urgence_enfant_idx ON famille.contact_urgence (enfant_id, rang);
