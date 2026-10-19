-- Actions rejouées hors ligne (FG-DOC-06 §5.4, US-SYS-002) : une écriture qui porte l'en-tête Idempotency-Key
-- n'est exécutée qu'une fois. Seuls le statut de la réponse et une empreinte de la requête sont gardés, jamais
-- son corps : aucune donnée d'un enfant ne passe par cette table. Les lignes sont effacées au bout de 24 heures.
CREATE SCHEMA IF NOT EXISTS plateforme;

CREATE TABLE plateforme.requete_idempotente (
    sujet       VARCHAR(80)  NOT NULL,
    cle         VARCHAR(80)  NOT NULL,
    -- SHA-256 de la méthode, du chemin et du corps : la même clé ne peut pas servir à une autre requête.
    empreinte   CHAR(64)     NOT NULL,
    -- NULL tant que la première exécution n'est pas terminée.
    statut      INTEGER,
    creee_le    TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (sujet, cle)
);

CREATE INDEX requete_idempotente_creee_le ON plateforme.requete_idempotente (creee_le);
