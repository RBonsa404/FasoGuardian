-- Invitation d'un second tuteur (US-PAR-003, écran 34). L'invité passe sa propre vérification KYC : son
-- dossier vise l'enfant déjà suivi, et l'approbation crée son lien de tutelle. Le numéro de l'invité n'est
-- gardé, chiffré avec son empreinte, que tant que l'invitation est ouverte.
CREATE TABLE identite.invitation_tuteur (
    id                 UUID         NOT NULL PRIMARY KEY,
    enfant_id          UUID         NOT NULL,
    invitant_id        UUID         NOT NULL REFERENCES identite.utilisateur (id),
    telephone_chiffre  BYTEA,
    telephone_hash     VARCHAR(64),
    statut             VARCHAR(16)  NOT NULL
        CHECK (statut IN ('EN_ATTENTE', 'EN_VERIFICATION', 'ACCEPTEE', 'ANNULEE', 'EXPIREE')),
    cree_le            TIMESTAMPTZ  NOT NULL,
    expire_le          TIMESTAMPTZ  NOT NULL,
    close_le           TIMESTAMPTZ,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT invitation_ouverte_avec_telephone CHECK (
        (statut IN ('EN_ATTENTE', 'EN_VERIFICATION')) = (telephone_chiffre IS NOT NULL AND telephone_hash IS NOT NULL)),
    CONSTRAINT invitation_close_datee CHECK ((statut IN ('EN_ATTENTE', 'EN_VERIFICATION')) = (close_le IS NULL))
);

-- Une seule invitation ouverte par enfant.
CREATE UNIQUE INDEX invitation_tuteur_ouverte_idx ON identite.invitation_tuteur (enfant_id)
    WHERE statut IN ('EN_ATTENTE', 'EN_VERIFICATION');
CREATE INDEX invitation_tuteur_telephone_idx ON identite.invitation_tuteur (telephone_hash)
    WHERE telephone_hash IS NOT NULL;

-- Dossier ouvert sur invitation ; la purge d'une invitation close n'efface pas le dossier.
ALTER TABLE identite.dossier_kyc
    ADD COLUMN invitation_id UUID REFERENCES identite.invitation_tuteur (id) ON DELETE SET NULL;
