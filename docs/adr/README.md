# Décisions d'architecture (ADR)

Les décisions ADR-01 à ADR-08 du dossier d'architecture (FG-DOC-06, tableau 3) restent la référence.
Ce dossier consigne les décisions prises pendant le développement, en particulier chaque contradiction
relevée entre les documents de conception, le brief de développement et le paquet de design.

Ordre de priorité des sources : FG-DOC-04 > FG-DOC-06 > FG-DOC-05 > FG-DOC-07 > FG-DOC-08 > design.

| N° | Décision | Statut |
|---|---|---|
| [0001](0001-site-vitrine-et-organisation-du-depot.md) | Site vitrine en quatrième application ; organisation `projects/` | Acceptée |
| [0002](0002-page-qr-minimisation-renforcee.md) | Page publique QR : numéro du bracelet seulement, ni prénom ni initiale | Acceptée |
| [0003](0003-module-plateforme.md) | Socle technique partagé `plateforme` à côté des neuf modules métier | Acceptée |
| [0004](0004-versions-figees.md) | Versions figées au démarrage et écarts par rapport à FG-DOC-06 | Acceptée |
| [0005](0005-ecarts-du-design.md) | Sept écarts et ajouts du paquet de design | Acceptée en partie |
| [0006](0006-rendu-de-la-page-qr.md) | Page QR rendue par le serveur à partir d'un gabarit produit par Angular, sans script | Acceptée |
| [0007](0007-politique-de-mot-de-passe.md) | Mot de passe des parents : 10 caractères dont un chiffre (écart ASVS) | Acceptée |
| [0008](0008-chiffrement-dans-les-cas-d-usage.md) | Chiffrement appelé par les cas d'usage plutôt que par convertisseurs JPA | Acceptée |
| [0009](0009-cycle-de-vie-du-bracelet.md) | Identifiant d'appareil, perte (suivi 72 h), vol (révocation immédiate), casse et liste de révocation | Acceptée |
| [0010](0010-evaluation-des-safe-zones.md) | Appartenance calculée dans le domaine, sortie après présence et tolérance, positions imprécises ignorées | Acceptée |
| [0011](0011-alertes-et-retrait.md) | Gravité et notification des alertes, résolution par le système, journal en ajout seul, fenêtre de retrait | Acceptée |
| [0012](0012-commandes-signees.md) | Commandes vers le bracelet : signature ECDSA, destinataire, expiration, anti-rejeu, accusé et réémission | Acceptée |
| [0013](0013-escalade-et-dossier-de-signalement.md) | Escalade sous second facteur ; sans convention, dossier PDF remis par le parent, chiffré et gardé 30 jours | Acceptée |
| [0014](0014-notifications-push-et-repli-sms.md) | Notifications : choix du canal, Web Push chiffré, accusé et repli SMS à 60 s, adresses de livraison limitées | Acceptée |
| [0015](0015-abonnements-et-paiements.md) | Abonnement par enfant, activation sur notification signée seulement, relances et restriction, droits exposés aux autres modules | Acceptée |
| [0016](0016-conformite-droits-des-personnes-et-purges.md) | Garde-fou AIPD au démarrage, accès servi aussitôt, effacement exécuté sous 30 jours dans chaque module, registre des purges et rapport mensuel | Acceptée |
| [0017](0017-partage-temporaire-de-la-position.md) | Partage de la position par lien personnel envoyé à un contact d'urgence, borné dans le temps, révocable, sans rien montrer de l'enfant | Acceptée |
| [0018](0018-supervision-et-alertes-d-exploitation.md) | Disponibilité mesurée par minutes répondues, délai événement → notification au 95e centile, alerte d'exploitation au journal et dans Prometheus | Acceptée |
| [0019](0019-litige-de-filiation.md) | Litige sur un lien de tutelle : gel appliqué par un intercepteur, suspension conservatoire de la position, décision fondée, sécurité jamais suspendue | Acceptée |
| [0020](0020-session-expiree-et-copie-locale.md) | Réauthentification complète, copie locale minimale de la fiche effacée à la déconnexion, application gardée pour l'ouverture hors ligne | Acceptée |
| [0021](0021-support-et-base-de-connaissances.md) | Demandes de support suivies dans le compte, réponses jamais envoyées par SMS, articles en texte simple publiés par l'opérateur | Acceptée |
| [0022](0022-cascade-des-alertes-sans-reponse.md) | Alerte critique sans réponse : contacts d'urgence puis point de contact institutionnel, par SMS sans donnée de l'enfant, chaque étape au journal | Acceptée |
| [0023](0023-accuse-de-reception-des-forces-de-securite.md) | Espace des forces de sécurité : signalement retrouvé par référence exacte, accusé horodaté et notifié, aucune donnée de l'enfant hors dossier transmis par passerelle, accès fermé à 30 jours | Acceptée |
