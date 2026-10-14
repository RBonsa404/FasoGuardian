-- Suivi rapproché demandé par un tuteur depuis la carte (écran 18) : le bracelet passe en mode alerte
-- (une position toutes les 60 secondes) pour une durée limitée, sans qu'aucune alerte soit ouverte.
CREATE TABLE alertes.suivi_rapproche (
    enfant_id    UUID         NOT NULL PRIMARY KEY,
    demande_par  UUID         NOT NULL,
    debut        TIMESTAMPTZ  NOT NULL,
    jusqu_au     TIMESTAMPTZ  NOT NULL
);
