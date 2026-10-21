-- Modèles de réponse de l'opérateur support (US-PAR-017, écran 62) : un raccourci « /sangle » insère un texte
-- préparé dans la réponse, que l'opérateur relit et complète avant l'envoi. Aucune donnée d'un parent ici.

CREATE TABLE identite.modele_reponse (
    id          UUID           NOT NULL PRIMARY KEY,
    -- Saisi après une barre oblique dans la réponse : lettres minuscules, chiffres et tirets.
    raccourci   VARCHAR(30)    NOT NULL UNIQUE CHECK (raccourci ~ '^[a-z0-9][a-z0-9-]{0,29}$'),
    titre       VARCHAR(80)    NOT NULL,
    texte       VARCHAR(2000)  NOT NULL,
    auteur_id   UUID           NOT NULL,
    modifie_le  TIMESTAMPTZ    NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0
);
