-- Litige de filiation sur un compte actif (US-KYC-001) : mesures conservatoires puis décision.

CREATE SEQUENCE identite.reference_litige START 1;

CREATE TABLE identite.litige (
    id                         UUID         NOT NULL PRIMARY KEY,
    reference                  VARCHAR(16)  NOT NULL UNIQUE,
    -- Le lien contesté : ce tuteur, pour cet enfant.
    tuteur_id                  UUID         NOT NULL REFERENCES identite.utilisateur (id),
    enfant_id                  UUID         NOT NULL,
    motif                      VARCHAR(500) NOT NULL,
    statut                     VARCHAR(8)   NOT NULL CHECK (statut IN ('OUVERT', 'CLOS')),
    geolocalisation_suspendue  BOOLEAN      NOT NULL,
    ouvert_par                 UUID         NOT NULL,
    ouvert_le                  TIMESTAMPTZ  NOT NULL,
    echeance_le                TIMESTAMPTZ  NOT NULL,
    decision                   VARCHAR(16)  CHECK (decision IN ('LIEN_MAINTENU', 'LIEN_RETIRE')),
    fondement                  VARCHAR(24)  CHECK (fondement IN ('DECISION_DE_JUSTICE', 'ACCORD_ECRIT')),
    reference_du_fondement     VARCHAR(120),
    decide_par                 UUID,
    decide_le                  TIMESTAMPTZ,
    version                    BIGINT       NOT NULL DEFAULT 0,
    CHECK ((statut = 'CLOS') = (decision IS NOT NULL AND fondement IS NOT NULL AND decide_le IS NOT NULL))
);

-- Un seul litige ouvert par lien contesté.
CREATE UNIQUE INDEX litige_ouvert_idx ON identite.litige (tuteur_id, enfant_id) WHERE statut = 'OUVERT';
CREATE INDEX litige_statut_idx ON identite.litige (statut, ouvert_le);
