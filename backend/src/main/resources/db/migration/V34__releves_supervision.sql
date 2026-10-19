-- Supervision (US-ADM-004, ADR 0039) : relevé horaire des indicateurs de FG-DOC-06 tableau 20, pour que l'écran 72
-- montre leur évolution sur vingt-quatre heures sans dépendre de la télésurveillance. Une ligne par heure et par
-- indicateur ; chaque instance y verse ses propres mesures (pire valeur de l'heure, ou somme des décomptes).
CREATE TABLE audit.releve_supervision (
    heure        TIMESTAMPTZ       NOT NULL,
    indicateur   VARCHAR(32)       NOT NULL,
    valeur       DOUBLE PRECISION  NOT NULL,
    PRIMARY KEY (heure, indicateur)
);
