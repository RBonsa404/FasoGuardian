-- Offres, abonnements, paiements mobile money et reçus (REQ-MUST-21, 22 ; FG-DOC-07 §4.5 ; FG-DOC-11 §3.3).

CREATE TABLE abonnements.offre (
    code              VARCHAR(16)  NOT NULL PRIMARY KEY,
    libelle           VARCHAR(40)  NOT NULL,
    prix_fcfa         INTEGER      NOT NULL CHECK (prix_fcfa > 0),
    -- Intervalle entre deux positions en usage normal, nombre de Safe Zones, profondeur de l'historique.
    intervalle_s      INTEGER      NOT NULL,
    zones_maximum     INTEGER      NOT NULL,
    historique_jours  INTEGER      NOT NULL CHECK (historique_jours BETWEEN 1 AND 90),
    -- L'offre École n'est ouverte que par une convention avec un établissement.
    souscriptible     BOOLEAN      NOT NULL,
    rang              INTEGER      NOT NULL
);

INSERT INTO abonnements.offre (code, libelle, prix_fcfa, intervalle_s, zones_maximum, historique_jours, souscriptible, rang) VALUES
    ('ESSENTIEL',     'Essentiel',     1500, 900, 1, 30, TRUE,  1),
    ('INTERMEDIAIRE', 'Intermédiaire', 2250, 300, 3, 30, TRUE,  2),
    ('PREMIUM',       'Premium',       5000, 300, 3, 90, TRUE,  3),
    ('ECOLE',         'École',         4000, 300, 3, 30, FALSE, 4);

CREATE TABLE abonnements.abonnement (
    id                   UUID         NOT NULL PRIMARY KEY,
    tuteur_id            UUID         NOT NULL,
    enfant_id            UUID         NOT NULL UNIQUE,
    offre_code           VARCHAR(16)  NOT NULL REFERENCES abonnements.offre (code),
    statut               VARCHAR(12)  NOT NULL CHECK (statut IN ('EN_ATTENTE', 'ACTIF', 'EN_RETARD', 'RESTREINT')),
    prochaine_echeance   DATE,
    renouvellement_auto  BOOLEAN      NOT NULL,
    moyen                VARCHAR(16)  CHECK (moyen IN ('ORANGE_MONEY', 'MOOV_MONEY')),
    -- Numéro du portefeuille à solliciter au renouvellement : chiffré comme un téléphone, jamais affiché en entier.
    numero_chiffre       BYTEA,
    numero_masque        VARCHAR(24),
    -- Avancement des relances de l'échéance en cours (0 = aucune ; voir Abonnement.relancer).
    etape_relance        INTEGER      NOT NULL DEFAULT 0,
    cree_le              TIMESTAMPTZ  NOT NULL,
    modifie_le           TIMESTAMPTZ  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    CHECK ((statut = 'EN_ATTENTE') = (prochaine_echeance IS NULL))
);

CREATE INDEX abonnement_echeance_idx ON abonnements.abonnement (prochaine_echeance) WHERE prochaine_echeance IS NOT NULL;
CREATE INDEX abonnement_tuteur_idx ON abonnements.abonnement (tuteur_id);

CREATE TABLE abonnements.paiement (
    id                   UUID         NOT NULL PRIMARY KEY,
    abonnement_id        UUID         NOT NULL REFERENCES abonnements.abonnement (id),
    offre_code           VARCHAR(16)  NOT NULL REFERENCES abonnements.offre (code),
    montant_fcfa         INTEGER      NOT NULL CHECK (montant_fcfa > 0),
    moyen                VARCHAR(16)  NOT NULL CHECK (moyen IN ('ORANGE_MONEY', 'MOOV_MONEY')),
    statut               VARCHAR(12)  NOT NULL CHECK (statut IN ('INITIE', 'CONFIRME', 'ECHOUE', 'EXPIRE')),
    reference_operateur  VARCHAR(64)  UNIQUE,
    -- Une même demande rejouée (réseau instable) ne crée jamais deux paiements.
    cle_idempotence      VARCHAR(80)  NOT NULL UNIQUE,
    motif_echec          VARCHAR(120),
    initie_le            TIMESTAMPTZ  NOT NULL,
    conclu_le            TIMESTAMPTZ,
    version              BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX paiement_abonnement_idx ON abonnements.paiement (abonnement_id, initie_le DESC);
CREATE INDEX paiement_en_cours_idx ON abonnements.paiement (initie_le) WHERE statut = 'INITIE';

-- Numérotation continue des reçus : le compteur avance dans la transaction qui émet le reçu, donc sans trou
-- (une séquence en laisserait un à chaque transaction annulée).
CREATE TABLE abonnements.compteur_recus (
    unique_ligne  BOOLEAN  NOT NULL PRIMARY KEY DEFAULT TRUE CHECK (unique_ligne),
    dernier       BIGINT   NOT NULL
);

INSERT INTO abonnements.compteur_recus (dernier) VALUES (0);

CREATE TABLE abonnements.facture (
    numero          VARCHAR(24)  NOT NULL PRIMARY KEY,
    paiement_id     UUID         NOT NULL UNIQUE REFERENCES abonnements.paiement (id),
    abonnement_id   UUID         NOT NULL REFERENCES abonnements.abonnement (id),
    tuteur_id       UUID         NOT NULL,
    offre_libelle   VARCHAR(40)  NOT NULL,
    montant_fcfa    INTEGER      NOT NULL,
    moyen           VARCHAR(16)  NOT NULL,
    numero_masque   VARCHAR(24)  NOT NULL,
    periode_debut   DATE         NOT NULL,
    periode_fin     DATE         NOT NULL,
    emise_le        TIMESTAMPTZ  NOT NULL
);

CREATE INDEX facture_tuteur_idx ON abonnements.facture (tuteur_id, emise_le DESC);
