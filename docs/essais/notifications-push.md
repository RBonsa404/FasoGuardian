# Essai manuel — notifications push sur téléphone

Ce que les tests automatisés prouvent déjà : le chiffrement du contenu et le jeton du serveur (`NotificationsIT`,
où le test joue le navigateur), le repli SMS, et le fait que le service worker traite un message poussé et en
accuse réception (`notifications.spec.ts`). Ce qu'ils ne peuvent pas prouver : l'affichage réel de la
notification, que le navigateur d'essai sans affichage refuse toujours, et la livraison par les services de
push de Google, Mozilla et Apple. Cet essai couvre ces deux points.

## Conditions

- Plateforme déployée en HTTPS (les notifications push n'existent pas en HTTP hors `localhost`), avec
  `FG_PUSH_CLE_PUBLIQUE`, `FG_PUSH_CLE_PRIVEE` et `FG_PUSH_SUJET` renseignées.
- Un compte parent actif, un enfant, un bracelet appairé (réel ou simulé).
- Un téléphone Android avec Chrome, un iPhone avec Safari (iOS 16.4 ou plus, application ajoutée à l'écran
  d'accueil : Safari n'envoie de push qu'aux applications installées), un ordinateur avec Firefox.

## Déroulé, sur chaque appareil

| # | Action | Résultat attendu |
|---|---|---|
| 1 | Réglages → « Notifications et installation », activer « Notifications d'alerte » | Le navigateur demande l'autorisation ; une fois accordée, l'écran indique que les alertes arrivent sur l'appareil |
| 2 | Fermer l'application. Déclencher un SOS sur le bracelet | En moins de 60 s : notification « Alerte SOS » qui reste à l'écran, **et** SMS |
| 3 | Toucher la notification | L'application s'ouvre sur l'alerte |
| 4 | Lever l'alerte. Déclencher une batterie critique (ou une sortie de zone) | Notification seule ; **aucun SMS** dans les 2 minutes qui suivent |
| 5 | Couper les données et le Wi-Fi du téléphone. Déclencher une sortie de zone, attendre 90 s | SMS de repli reçu ; la notification arrive au retour du réseau |
| 6 | Désactiver « Notifications d'alerte ». Déclencher une batterie critique | SMS immédiat, aucune notification |
| 7 | Bloquer les notifications du site dans les réglages du navigateur, rouvrir l'écran | L'écran indique que les notifications sont bloquées et que les alertes arrivent par SMS |

## Relevé

Noter pour chaque appareil : modèle, version du système et du navigateur, opérateur, délai mesuré entre
l'événement et la notification aux étapes 2 et 4, et tout écart. Un délai supérieur à 45 s au 95e centile est
un écart à l'objectif de FG-DOC-04 (US-ADM, supervision) et doit être consigné.
