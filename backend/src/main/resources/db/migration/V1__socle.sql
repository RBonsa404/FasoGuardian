-- Socle : extension spatiale, un schéma par module (FG-DOC-06 §7.1) et tables techniques.

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE SCHEMA IF NOT EXISTS identite;
CREATE SCHEMA IF NOT EXISTS famille;
CREATE SCHEMA IF NOT EXISTS dispositifs;
CREATE SCHEMA IF NOT EXISTS telemetrie;
CREATE SCHEMA IF NOT EXISTS geolocalisation;
CREATE SCHEMA IF NOT EXISTS alertes;
CREATE SCHEMA IF NOT EXISTS notifications;
CREATE SCHEMA IF NOT EXISTS abonnements;
CREATE SCHEMA IF NOT EXISTS audit;

-- Verrous des traitements planifiés (ShedLock).
CREATE TABLE plateforme.shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- Registre de publication des événements de domaine (Spring Modulith) :
-- un redémarrage du serveur n'entraîne la perte d'aucun événement en cours de traitement.
CREATE TABLE plateforme.event_publication (
    id                     UUID         NOT NULL PRIMARY KEY,
    listener_id            TEXT         NOT NULL,
    event_type             TEXT         NOT NULL,
    serialized_event       TEXT         NOT NULL,
    publication_date       TIMESTAMPTZ  NOT NULL,
    completion_date        TIMESTAMPTZ,
    status                 VARCHAR(32),
    completion_attempts    INTEGER      NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMPTZ
);

CREATE INDEX event_publication_en_attente_idx
    ON plateforme.event_publication (publication_date)
    WHERE completion_date IS NULL;
