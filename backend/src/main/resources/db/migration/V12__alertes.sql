-- Alertes, journal d'acquittement et autorisations de retrait (REQ-MUST-11, 12, 17 ; FG-DOC-07 §4.4 et §5).

-- Le bracelet signale aussi la reprise du contact peau, qui clôt un retrait (docs/protocole-bracelet.md).
ALTER TABLE telemetrie.evenement DROP CONSTRAINT evenement_type_check;
ALTER TABLE telemetrie.evenement ADD CONSTRAINT evenement_type_check CHECK (type IN
    ('SOS', 'COUPURE_BOUCLE', 'PERTE_CONTACT_PEAU', 'CHUTE', 'BATTERIE_CRITIQUE', 'MISE_EN_CHARGE', 'PORT_RETABLI'));

CREATE TABLE alertes.alerte (
    id             UUID              NOT NULL PRIMARY KEY,
    enfant_id      UUID              NOT NULL,
    type           VARCHAR(20)       NOT NULL CHECK (type IN
        ('SOS', 'RETRAIT', 'SORTIE_ZONE', 'BATTERIE_CRITIQUE', 'CHUTE', 'SIGNALEMENT')),
    gravite        VARCHAR(12)       NOT NULL CHECK (gravite IN ('CRITIQUE', 'IMPORTANTE')),
    statut         VARCHAR(16)       NOT NULL CHECK (statut IN
        ('OUVERTE', 'ACQUITTEE', 'ESCALADEE', 'LEVEE', 'FAUSSE_ALERTE')),
    ouverte_le     TIMESTAMPTZ       NOT NULL,
    close_le       TIMESTAMPTZ,
    -- Zone concernée par une sortie de zone et son nom au moment de l'alerte.
    zone_id        UUID,
    libelle        VARCHAR(40),
    -- Dernière position connue au déclenchement, si le bracelet l'a jointe.
    latitude       DOUBLE PRECISION,
    longitude      DOUBLE PRECISION,
    sms_envoye_le  TIMESTAMPTZ,
    version        BIGINT            NOT NULL DEFAULT 0,
    CHECK ((close_le IS NOT NULL) = (statut IN ('LEVEE', 'FAUSSE_ALERTE')))
);

CREATE INDEX alerte_enfant_idx ON alertes.alerte (enfant_id, statut);
CREATE INDEX alerte_date_idx ON alertes.alerte (enfant_id, ouverte_le DESC);

-- Journal d'acquittement : une ligne par transition, jamais modifiée ni supprimée (conservation 5 ans).
CREATE TABLE alertes.action_alerte (
    id            UUID          NOT NULL PRIMARY KEY,
    alerte_id     UUID          NOT NULL REFERENCES alertes.alerte (id),
    type          VARCHAR(16)   NOT NULL CHECK (type IN
        ('OUVERTURE', 'ACQUITTEMENT', 'ESCALADE', 'LEVEE', 'FAUSSE_ALERTE', 'RESOLUTION')),
    -- Tuteur à l'origine de l'action ; vide pour une action du système.
    acteur_id     UUID,
    motif         VARCHAR(200),
    effectuee_le  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX action_alerte_idx ON alertes.action_alerte (alerte_id, effectuee_le);

CREATE FUNCTION alertes.refuser_modification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Le journal des alertes est en ajout seul';
END;
$$;

CREATE TRIGGER action_alerte_ajout_seul
    BEFORE UPDATE OR DELETE ON alertes.action_alerte
    FOR EACH ROW EXECUTE FUNCTION alertes.refuser_modification();

CREATE TRIGGER action_alerte_sans_troncature
    BEFORE TRUNCATE ON alertes.action_alerte
    FOR EACH STATEMENT EXECUTE FUNCTION alertes.refuser_modification();

CREATE TABLE alertes.autorisation_retrait (
    id             UUID          NOT NULL PRIMARY KEY,
    enfant_id      UUID          NOT NULL,
    bracelet_id    UUID          NOT NULL,
    accordee_par   UUID          NOT NULL,
    motif          VARCHAR(12)   NOT NULL CHECK (motif IN ('TOILETTE', 'RECHARGE', 'NUIT', 'AUTRE')),
    debut          TIMESTAMPTZ   NOT NULL,
    fin            TIMESTAMPTZ   NOT NULL,
    statut         VARCHAR(12)   NOT NULL CHECK (statut IN ('ACTIVE', 'TERMINEE', 'ECHUE')),
    -- Le bracelet a été retiré pendant la fenêtre et n'a pas encore été remis.
    retire         BOOLEAN       NOT NULL,
    rappel_envoye  BOOLEAN       NOT NULL,
    cloturee_le    TIMESTAMPTZ,
    version        BIGINT        NOT NULL DEFAULT 0,
    CHECK (fin > debut)
);

-- Une seule autorisation active par bracelet.
CREATE UNIQUE INDEX autorisation_active_idx ON alertes.autorisation_retrait (bracelet_id) WHERE statut = 'ACTIVE';
CREATE INDEX autorisation_enfant_idx ON alertes.autorisation_retrait (enfant_id, debut DESC);
