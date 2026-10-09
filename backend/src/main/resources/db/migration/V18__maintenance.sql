-- Suivi des bracelets en service (US-SAV-001, US-SYS-007) : tickets de maintenance, mode économie d'office.

CREATE SEQUENCE dispositifs.reference_ticket START 1;

CREATE TABLE dispositifs.ticket_maintenance (
    id               UUID         NOT NULL PRIMARY KEY,
    reference        VARCHAR(16)  NOT NULL UNIQUE,
    bracelet_id      UUID         NOT NULL REFERENCES dispositifs.bracelet (id),
    motif            VARCHAR(16)  NOT NULL CHECK (motif IN ('MUET')),
    statut           VARCHAR(12)  NOT NULL CHECK (statut IN ('OUVERT', 'EN_COURS', 'RESOLU')),
    ouvert_le        TIMESTAMPTZ  NOT NULL,
    -- État du bracelet relevé à l'ouverture, pour le diagnostic : aucune donnée de l'enfant.
    dernier_contact  TIMESTAMPTZ,
    batterie         INTEGER,
    reseau           VARCHAR(8),
    agent_id         UUID,
    pris_en_charge_le TIMESTAMPTZ,
    resolution       VARCHAR(24)  CHECK (resolution IN ('REPRISE_SPONTANEE', 'RECHARGE', 'ECHANGE', 'RETOUR_ATELIER', 'SANS_SUITE')),
    note             VARCHAR(300),
    resolu_le        TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    CHECK ((statut = 'RESOLU') = (resolu_le IS NOT NULL AND resolution IS NOT NULL))
);

-- Un seul ticket ouvert par bracelet et par motif : un bracelet muet n'ouvre pas un ticket par minute.
CREATE UNIQUE INDEX ticket_ouvert_idx ON dispositifs.ticket_maintenance (bracelet_id, motif) WHERE statut <> 'RESOLU';
CREATE INDEX ticket_statut_idx ON dispositifs.ticket_maintenance (statut, ouvert_le);

-- Vrai quand le mode économie a été activé par la plateforme sous 20 % de batterie : elle le lève à la recharge,
-- sans toucher à un mode économie choisi par le parent.
ALTER TABLE dispositifs.configuration ADD COLUMN economie_automatique BOOLEAN NOT NULL DEFAULT FALSE;
