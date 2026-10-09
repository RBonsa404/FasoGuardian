-- Escalade vers les forces de sécurité et dossier de signalement (REQ-MUST-12, REQ-SYS-022 ; FG-DOC-07 §4.4).

CREATE SEQUENCE alertes.reference_signalement START 1;

CREATE TABLE alertes.signalement_fds (
    alerte_id          UUID          NOT NULL PRIMARY KEY REFERENCES alertes.alerte (id),
    reference          VARCHAR(16)   NOT NULL UNIQUE,
    -- REMISE_PAR_LE_PARENT tant qu'aucune convention n'est signée avec la Police et la Gendarmerie.
    canal              VARCHAR(24)   NOT NULL CHECK (canal IN ('REMISE_PAR_LE_PARENT', 'PASSERELLE')),
    -- Empreinte SHA-256 du dossier tel qu'il a été généré : elle reste après l'effacement du dossier.
    empreinte_dossier  VARCHAR(64)   NOT NULL,
    -- Dossier PDF chiffré, effacé au bout de 30 jours.
    dossier_chiffre    BYTEA,
    cree_le            TIMESTAMPTZ   NOT NULL,
    transmis_le        TIMESTAMPTZ,
    accuse_le          TIMESTAMPTZ,
    efface_le          TIMESTAMPTZ
);

CREATE INDEX signalement_a_effacer_idx ON alertes.signalement_fds (cree_le) WHERE dossier_chiffre IS NOT NULL;
