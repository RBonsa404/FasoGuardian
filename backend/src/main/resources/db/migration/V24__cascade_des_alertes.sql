-- Sollicitation en cascade quand une alerte critique reste sans réponse (US-SYS-005) : chaque étape est une
-- action de plus au journal de l'alerte.
ALTER TABLE alertes.action_alerte ALTER COLUMN type TYPE VARCHAR(24);
ALTER TABLE alertes.action_alerte DROP CONSTRAINT action_alerte_type_check;
ALTER TABLE alertes.action_alerte ADD CONSTRAINT action_alerte_type_check CHECK (type IN
    ('OUVERTURE', 'ACQUITTEMENT', 'ESCALADE', 'LEVEE', 'FAUSSE_ALERTE', 'RESOLUTION', 'CONTACT_SOLLICITE', 'INSTITUTION_SOLLICITEE'));
