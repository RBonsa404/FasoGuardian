-- Repli SMS du bracelet (US-SYS-001) : la plateforme vérifie la signature de chaque SMS avec la clé publique
-- du bracelet, relevée à l'atelier sur son certificat. Sans clé enregistrée, ses SMS sont rejetés.
ALTER TABLE dispositifs.bracelet ADD COLUMN cle_publique BYTEA;
