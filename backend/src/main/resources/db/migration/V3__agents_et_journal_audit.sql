-- Agents internes (identite) et journal d'audit en ajout seul, chaîné par empreinte (audit).

ALTER TABLE identite.utilisateur
    ADD COLUMN identifiant          VARCHAR(64) UNIQUE,
    ADD COLUMN roles                VARCHAR(200),
    -- Secret TOTP chiffré AES-256-GCM (catégorie SECRET_MFA).
    ADD COLUMN totp_secret_chiffre  BYTEA,
    ADD COLUMN totp_actif           BOOLEAN NOT NULL DEFAULT FALSE,
    -- Dernier pas de temps accepté : un code TOTP ne peut pas être rejoué.
    ADD COLUMN totp_dernier_pas     BIGINT  NOT NULL DEFAULT 0,
    ADD CONSTRAINT agent_avec_identifiant CHECK (type <> 'AGENT' OR (identifiant IS NOT NULL AND roles IS NOT NULL));

CREATE TABLE audit.entree (
    id                    BIGINT       NOT NULL PRIMARY KEY,
    horodatage            TIMESTAMPTZ  NOT NULL,
    acteur_id             UUID,
    role                  VARCHAR(64)  NOT NULL,
    action                VARCHAR(64)  NOT NULL,
    type_cible            VARCHAR(64)  NOT NULL,
    cible_id              VARCHAR(128),
    resultat              VARCHAR(16)  NOT NULL CHECK (resultat IN ('SUCCES', 'REFUS')),
    empreinte_precedente  VARCHAR(64)  NOT NULL,
    empreinte             VARCHAR(64)  NOT NULL UNIQUE
);

CREATE INDEX entree_cible_idx ON audit.entree (type_cible, cible_id);
CREATE INDEX entree_acteur_idx ON audit.entree (acteur_id, horodatage);

-- Ajout seul : toute modification ou suppression est refusée par la base elle-même (REQ-SYS-019).
-- En exploitation, le rôle SQL applicatif ne reçoit en outre que INSERT et SELECT sur cette table.
CREATE FUNCTION audit.refuser_modification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Le journal d''audit est en ajout seul';
END;
$$;

CREATE TRIGGER entree_ajout_seul
    BEFORE UPDATE OR DELETE ON audit.entree
    FOR EACH ROW EXECUTE FUNCTION audit.refuser_modification();

CREATE TRIGGER entree_sans_troncature
    BEFORE TRUNCATE ON audit.entree
    FOR EACH STATEMENT EXECUTE FUNCTION audit.refuser_modification();
