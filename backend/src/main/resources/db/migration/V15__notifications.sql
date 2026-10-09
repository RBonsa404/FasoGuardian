-- Notifications aux utilisateurs : Web Push, repli SMS (REQ-MUST-11 ; FG-DOC-07 §4.4).

-- Abonnement d'un navigateur aux notifications push de l'application.
CREATE TABLE notifications.abonnement_push (
    id                 UUID          NOT NULL PRIMARY KEY,
    destinataire_id    UUID          NOT NULL,
    -- Adresse fournie par le service de push du navigateur ; elle est vérifiée contre une liste d'hôtes admis.
    point_de_livraison TEXT          NOT NULL UNIQUE,
    cle_p256dh         VARCHAR(128)  NOT NULL,
    secret_auth        VARCHAR(64)   NOT NULL,
    cree_le            TIMESTAMPTZ   NOT NULL,
    dernier_succes_le  TIMESTAMPTZ
);

CREATE INDEX abonnement_push_destinataire_idx ON notifications.abonnement_push (destinataire_id, cree_le);

-- Message adressé à un destinataire. Il ne contient ni donnée de santé ni coordonnée (FG-DOC-06 §6.6).
CREATE TABLE notifications.notification (
    id               UUID          NOT NULL PRIMARY KEY,
    destinataire_id  UUID          NOT NULL,
    modele           VARCHAR(40)   NOT NULL,
    urgence          VARCHAR(12)   NOT NULL CHECK (urgence IN ('CRITIQUE', 'IMPORTANTE', 'INFORMATION')),
    titre            VARCHAR(80)   NOT NULL,
    texte            VARCHAR(240)  NOT NULL,
    lien             VARCHAR(120),
    -- Objet métier concerné (« alerte:<id> ») : son traitement vaut accusé et annule le repli SMS.
    reference        VARCHAR(64),
    creee_le         TIMESTAMPTZ   NOT NULL,
    poussee_le       TIMESTAMPTZ,
    accusee_le       TIMESTAMPTZ,
    sms_envoye_le    TIMESTAMPTZ
);

CREATE INDEX notification_repli_idx ON notifications.notification (creee_le)
    WHERE urgence = 'IMPORTANTE' AND accusee_le IS NULL AND sms_envoye_le IS NULL;
CREATE INDEX notification_reference_idx ON notifications.notification (reference);
CREATE INDEX notification_date_idx ON notifications.notification (creee_le);

-- Le repli SMS des alertes est désormais tenu par le module notifications.
ALTER TABLE alertes.alerte DROP COLUMN sms_envoye_le;
