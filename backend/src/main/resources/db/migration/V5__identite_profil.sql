-- Profil du parent : horodatage des changements de coordonnées et de la clôture (US-PAR-002).

ALTER TABLE identite.utilisateur
    ADD COLUMN telephone_modifie_le TIMESTAMPTZ,
    ADD COLUMN clos_le              TIMESTAMPTZ;

CREATE INDEX utilisateur_clos_idx ON identite.utilisateur (clos_le) WHERE clos_le IS NOT NULL;
