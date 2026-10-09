-- Conformité (US-ADM-003, FG-DOC-06 tableau 18) : registre des purges, demandes d'accès et d'effacement.

-- Chaque exécution d'une purge planifiée, pour le rapport de conformité.
CREATE TABLE audit.execution_purge (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    traitement   VARCHAR(48)  NOT NULL,
    executee_le  TIMESTAMPTZ  NOT NULL,
    elements     BIGINT       NOT NULL CHECK (elements >= 0)
);

CREATE INDEX execution_purge_traitement_idx ON audit.execution_purge (traitement, executee_le DESC);

CREATE SEQUENCE audit.reference_demande START 1;

-- Exercice des droits des personnes : un accès est servi aussitôt, un effacement dans les 30 jours.
CREATE TABLE audit.demande_droit (
    id            UUID         NOT NULL PRIMARY KEY,
    reference     VARCHAR(16)  NOT NULL UNIQUE,
    type          VARCHAR(12)  NOT NULL CHECK (type IN ('ACCES', 'EFFACEMENT')),
    demandeur_id  UUID         NOT NULL,
    statut        VARCHAR(12)  NOT NULL CHECK (statut IN ('RECUE', 'TRAITEE')),
    recue_le      TIMESTAMPTZ  NOT NULL,
    echeance_le   TIMESTAMPTZ  NOT NULL,
    traitee_le    TIMESTAMPTZ,
    -- Agent qui a exécuté la demande ; vide quand le système l'a fait à l'approche de l'échéance.
    traitee_par   UUID,
    version       BIGINT       NOT NULL DEFAULT 0,
    CHECK ((statut = 'TRAITEE') = (traitee_le IS NOT NULL))
);

CREATE INDEX demande_droit_statut_idx ON audit.demande_droit (statut, echeance_le);
CREATE UNIQUE INDEX demande_effacement_en_cours_idx ON audit.demande_droit (demandeur_id) WHERE type = 'EFFACEMENT' AND statut = 'RECUE';

-- Un compte effacé ne garde ni numéro ni mot de passe : seule sa ligne technique subsiste, pour l'intégrité
-- des références. Le numéro redevient disponible pour une nouvelle inscription.
ALTER TABLE identite.utilisateur
    ADD COLUMN efface_le TIMESTAMPTZ,
    DROP CONSTRAINT tuteur_avec_telephone,
    ADD CONSTRAINT tuteur_avec_telephone CHECK (type <> 'TUTEUR' OR efface_le IS NOT NULL
        OR (telephone_chiffre IS NOT NULL AND telephone_hash IS NOT NULL));

-- Le journal des révisions de la fiche santé est en ajout seul et ne porte que des décomptes : à l'effacement
-- d'un enfant, ses lignes restent, rattachées à un identifiant qui ne désigne plus personne.
ALTER TABLE famille.revision_sante DROP CONSTRAINT revision_sante_enfant_id_fkey;

-- L'offre Essentiel ne comprend pas d'historique étendu : 24 heures (FG-DOC-11 §3.3, écran 71).
UPDATE abonnements.offre SET historique_jours = 1 WHERE code = 'ESSENTIEL';
