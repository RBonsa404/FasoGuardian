-- Page publique QR : profil du jeton gravé, journal des consultations, messages des tiers (REQ-SYS-014).

CREATE TABLE famille.profil_qr (
    enfant_id        UUID         NOT NULL PRIMARY KEY REFERENCES famille.enfant (id),
    -- Seule l'empreinte SHA-256 du jeton (128 bits aléatoires) est conservée.
    jeton_sha256     VARCHAR(64)  NOT NULL UNIQUE,
    numero_bracelet  VARCHAR(32)  NOT NULL,
    statut           VARCHAR(16)  NOT NULL CHECK (statut IN ('ACTIF', 'SUSPENDU')),
    associe_le       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0
);

-- Journal des consultations : aucune identité du visiteur, adresse IP pseudonymisée, conservation 12 mois
-- (purge par suppression de partition).
CREATE TABLE famille.consultation_qr (
    id                 UUID         NOT NULL,
    consulte_le        TIMESTAMPTZ  NOT NULL,
    enfant_id          UUID,
    ip_pseudonymisee   VARCHAR(64)  NOT NULL,
    resultat           VARCHAR(16)  NOT NULL CHECK (resultat IN ('TROUVE', 'INCONNU', 'DESACTIVE')),
    PRIMARY KEY (id, consulte_le)
) PARTITION BY RANGE (consulte_le);

CREATE INDEX consultation_qr_date_idx ON famille.consultation_qr USING BRIN (consulte_le);
CREATE INDEX consultation_qr_enfant_idx ON famille.consultation_qr (enfant_id, consulte_le DESC);

-- Crée la partition mensuelle contenant la date donnée, si elle n'existe pas.
CREATE FUNCTION famille.creer_partition_consultation_qr(jour DATE) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    debut DATE := date_trunc('month', jour)::date;
    nom   TEXT := 'consultation_qr_' || to_char(debut, 'YYYY_MM');
BEGIN
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS famille.%I PARTITION OF famille.consultation_qr FOR VALUES FROM (%L) TO (%L)',
        nom, debut, (debut + INTERVAL '1 month')::date);
END;
$$;

-- Supprime les partitions entièrement antérieures à la durée de conservation ; renvoie leur nombre.
CREATE FUNCTION famille.purger_consultations_qr(mois_conserves INTEGER) RETURNS INTEGER LANGUAGE plpgsql AS $$
DECLARE
    limite    TEXT := 'consultation_qr_' || to_char(date_trunc('month', now()) - make_interval(months => mois_conserves), 'YYYY_MM');
    partition RECORD;
    nombre    INTEGER := 0;
BEGIN
    FOR partition IN
        SELECT c.relname FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid
        JOIN pg_class p ON p.oid = i.inhparent
        JOIN pg_namespace n ON n.oid = p.relnamespace
        WHERE n.nspname = 'famille' AND p.relname = 'consultation_qr' AND c.relname < limite
    LOOP
        EXECUTE format('DROP TABLE famille.%I', partition.relname);
        nombre := nombre + 1;
    END LOOP;
    RETURN nombre;
END;
$$;

SELECT famille.creer_partition_consultation_qr((CURRENT_DATE + make_interval(months => decalage))::date)
FROM generate_series(0, 2) AS decalage;

-- Message laissé par la personne qui a trouvé l'enfant : numéro et lieu chiffrés, conservés 30 jours.
CREATE TABLE famille.signalement_tiers (
    id                 UUID         NOT NULL PRIMARY KEY,
    enfant_id          UUID         NOT NULL REFERENCES famille.enfant (id),
    telephone_chiffre  BYTEA        NOT NULL,
    lieu_chiffre       BYTEA,
    recu_le            TIMESTAMPTZ  NOT NULL
);

CREATE INDEX signalement_tiers_enfant_idx ON famille.signalement_tiers (enfant_id, recu_le DESC);
