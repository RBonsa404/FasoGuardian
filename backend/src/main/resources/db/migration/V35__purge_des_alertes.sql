-- Conservation des alertes (FG-DOC-06, tableau 18) : 5 ans après leur clôture, à des fins probatoires, puis
-- effacement. Le journal d'acquittement reste en ajout seul : aucune modification n'est jamais permise, et une
-- suppression ne l'est que pendant la purge planifiée (marqueur de transaction posé par PurgesAlertes) et pour
-- une alerte close depuis plus de 5 ans. Une alerte ouverte, ou close plus récemment, n'est jamais effacée.
CREATE OR REPLACE FUNCTION alertes.refuser_modification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE'
            AND current_setting('fasoguardian.purge_alertes', true) = 'oui'
            AND EXISTS (SELECT 1 FROM alertes.alerte a
                        WHERE a.id = OLD.alerte_id AND a.close_le < now() - INTERVAL '5 years') THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Le journal des alertes est en ajout seul';
END;
$$;

CREATE INDEX alerte_close_idx ON alertes.alerte (close_le) WHERE close_le IS NOT NULL;
