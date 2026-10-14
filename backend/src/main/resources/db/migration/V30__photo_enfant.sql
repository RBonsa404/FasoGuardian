-- Photo de l'enfant (US-PAR-004). Chiffrée (catégorie PROFIL_ENFANT), visible des seuls tuteurs et reprise
-- dans le dossier de signalement (US-PAR-010) ; jamais sur la page publique du QR (ADR 0002).
CREATE TABLE famille.photo_enfant (
    enfant_id        UUID         NOT NULL PRIMARY KEY REFERENCES famille.enfant (id),
    contenu_chiffre  BYTEA        NOT NULL,
    type_mime        VARCHAR(16)  NOT NULL CHECK (type_mime IN ('image/jpeg', 'image/png')),
    taille_octets    INTEGER      NOT NULL,
    modifiee_le      TIMESTAMPTZ  NOT NULL
);

-- Référence prévue dès V6 pour un stockage externe, jamais employée : la photo vit chiffrée en base.
ALTER TABLE famille.enfant DROP COLUMN photo_ref;
