-- Traitement d'un bracelet muet jusqu'à l'échange en point relais (US-SAV-001, écran 67).

ALTER TABLE dispositifs.ticket_maintenance
    ADD COLUMN diagnostic_le          TIMESTAMPTZ,
    ADD COLUMN commande_diagnostic_id UUID REFERENCES dispositifs.commande (id),
    ADD COLUMN point_relais           VARCHAR(50),
    ADD COLUMN rendez_vous_le         TIMESTAMPTZ,
    ADD COLUMN remplacement_id        UUID REFERENCES dispositifs.bracelet (id),
    ADD CHECK ((point_relais IS NULL) = (rendez_vous_le IS NULL)),
    ADD CHECK ((diagnostic_le IS NULL) = (commande_diagnostic_id IS NULL));

-- Une unité de remplacement n'est réservée que pour un ticket en cours à la fois.
CREATE UNIQUE INDEX ticket_remplacement_idx ON dispositifs.ticket_maintenance (remplacement_id)
    WHERE statut <> 'RESOLU' AND remplacement_id IS NOT NULL;
