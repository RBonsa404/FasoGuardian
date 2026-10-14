-- Lecture d'une notification dans l'application (centre des alertes, écran 24), distincte de l'accusé de
-- livraison du push : une notification livrée n'est pas forcément lue.
ALTER TABLE notifications.notification ADD COLUMN lue_le TIMESTAMPTZ;
