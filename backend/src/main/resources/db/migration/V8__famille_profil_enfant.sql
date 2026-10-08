-- Profil descriptif de l'enfant (école, quartier, taille, signes distinctifs) : JSON chiffré AES-256-GCM,
-- catégorie PROFIL_ENFANT. Ces éléments aident à reconnaître l'enfant ; ils ne sortent jamais sur la page publique.

ALTER TABLE famille.enfant ADD COLUMN profil_chiffre BYTEA;
