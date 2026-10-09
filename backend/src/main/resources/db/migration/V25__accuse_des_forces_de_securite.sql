-- Accusé de réception d'un signalement par les forces de sécurité (US-FDS-001) : qui l'a donné.
ALTER TABLE alertes.signalement_fds ADD COLUMN accuse_par UUID;
