-- Supervision (US-ADM-004) : une ligne par minute où le serveur a répondu et joint sa base. Les minutes sans
-- ligne sont des minutes d'indisponibilité : la disponibilité mensuelle s'en déduit sans outil externe.
CREATE TABLE audit.sonde_disponibilite (
    minute  TIMESTAMPTZ  NOT NULL PRIMARY KEY
);
