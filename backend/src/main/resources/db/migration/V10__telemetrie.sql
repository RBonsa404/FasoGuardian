-- Télémétrie : positions et événements en ajout seul, partitionnés par mois ; dernier état connu du bracelet
-- (REQ-MUST-09, 14, 15, 16 ; FG-DOC-06 §7.2 ; FG-DOC-07 tableau 5).

-- Les positions ne sont pas chiffrées au niveau applicatif : les calculs spatiaux l'interdisent
-- (FG-DOC-06 §8.3). Elles ne portent aucune identité, seulement l'identifiant interne du bracelet.
CREATE TABLE telemetrie.position (
    id           UUID                   NOT NULL,
    mesuree_le   TIMESTAMPTZ            NOT NULL,
    bracelet_id  UUID                   NOT NULL,
    point        GEOGRAPHY(Point, 4326) NOT NULL,
    precision_m  INTEGER                NOT NULL CHECK (precision_m BETWEEN 0 AND 100000),
    source       VARCHAR(8)             NOT NULL CHECK (source IN ('GNSS', 'CELLULE', 'WIFI')),
    sequence     BIGINT                 NOT NULL,
    recue_le     TIMESTAMPTZ            NOT NULL,
    PRIMARY KEY (id, mesuree_le),
    -- Idempotence : un message rejoué (tampon hors ligne, QoS 1) est ignoré sans erreur.
    UNIQUE (bracelet_id, sequence, mesuree_le)
) PARTITION BY RANGE (mesuree_le);

CREATE INDEX position_bracelet_idx ON telemetrie.position (bracelet_id, mesuree_le DESC);
CREATE INDEX position_point_idx ON telemetrie.position USING GIST (point);
CREATE INDEX position_date_idx ON telemetrie.position USING BRIN (mesuree_le);

CREATE TABLE telemetrie.evenement (
    id           UUID                   NOT NULL,
    mesure_le    TIMESTAMPTZ            NOT NULL,
    bracelet_id  UUID                   NOT NULL,
    type         VARCHAR(24)            NOT NULL CHECK (type IN
        ('SOS', 'COUPURE_BOUCLE', 'PERTE_CONTACT_PEAU', 'CHUTE', 'BATTERIE_CRITIQUE', 'MISE_EN_CHARGE')),
    point        GEOGRAPHY(Point, 4326),
    sequence     BIGINT                 NOT NULL,
    recu_le      TIMESTAMPTZ            NOT NULL,
    PRIMARY KEY (id, mesure_le),
    UNIQUE (bracelet_id, sequence, mesure_le)
) PARTITION BY RANGE (mesure_le);

CREATE INDEX evenement_bracelet_idx ON telemetrie.evenement (bracelet_id, mesure_le DESC);

-- Crée la partition mensuelle de la table donnée contenant le jour donné, si elle n'existe pas.
CREATE FUNCTION telemetrie.creer_partition(nom_table TEXT, jour DATE) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    debut DATE := date_trunc('month', jour)::date;
BEGIN
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS telemetrie.%I PARTITION OF telemetrie.%I FOR VALUES FROM (%L) TO (%L)',
        nom_table || '_' || to_char(debut, 'YYYY_MM'), nom_table, debut, (debut + INTERVAL '1 month')::date);
END;
$$;

-- Supprime les partitions dont tout le contenu est antérieur à la date limite ; renvoie leur nombre.
CREATE FUNCTION telemetrie.purger_partitions(nom_table TEXT, limite DATE) RETURNS INTEGER LANGUAGE plpgsql AS $$
DECLARE
    seuil     TEXT := nom_table || '_' || to_char(date_trunc('month', limite), 'YYYY_MM');
    partition RECORD;
    nombre    INTEGER := 0;
BEGIN
    FOR partition IN
        SELECT c.relname FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid
        JOIN pg_class p ON p.oid = i.inhparent
        JOIN pg_namespace n ON n.oid = p.relnamespace
        WHERE n.nspname = 'telemetrie' AND p.relname = nom_table AND c.relname < seuil
    LOOP
        EXECUTE format('DROP TABLE telemetrie.%I', partition.relname);
        nombre := nombre + 1;
    END LOOP;
    RETURN nombre;
END;
$$;

-- Partitions du mois précédent (positions tamponnées hors ligne) au mois suivant.
SELECT telemetrie.creer_partition(nom, (CURRENT_DATE + make_interval(months => decalage))::date)
FROM generate_series(-1, 2) AS decalage, unnest(ARRAY['position', 'evenement']) AS nom;

-- Dernier état connu, remplacé à chaque message (FG-DOC-07 : EtatBracelet).
CREATE TABLE telemetrie.etat_bracelet (
    bracelet_id       UUID         NOT NULL PRIMARY KEY,
    batterie          SMALLINT     CHECK (batterie BETWEEN 0 AND 100),
    signal_dbm        SMALLINT,
    reseau            VARCHAR(4)   CHECK (reseau IN ('2G', '3G', '4G')),
    operateur         VARCHAR(24),
    en_mouvement      BOOLEAN,
    en_ligne          BOOLEAN,
    version_logiciel  VARCHAR(16),
    dernier_contact   TIMESTAMPTZ  NOT NULL
);
