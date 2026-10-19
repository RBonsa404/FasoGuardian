# ADR 0039 — Historique de la supervision dans la console

Statut : acceptée — 19 octobre 2026

## Contexte

L'écran 72 de la maquette montre six indicateurs, chacun avec sa valeur, un objectif et une évolution en barres.
L'écran livré par l'ADR 0018 n'en avait que les valeurs instantanées, et des tuiles différentes. FG-DOC-06
(tableau 20) liste exactement les six indicateurs de la maquette, avec ses propres objectifs : disponibilité
d'au moins 99,5 %, délai événement → notification sous 60 s (alerte au-delà de 45 s), latence des API sous
500 ms (alerte au-delà de 800 ms), messages MQTT en erreur sous 0,5 % (alerte au-delà de 2 %), bracelets muets
(information, alerte sur une hausse de plus de 10 % du parc actif), espace disque de la base sous 70 % (alerte
au-delà de 80 %). Les objectifs de la maquette (« < 10 s », « seuil 1 % »…) s'écartent de FG-DOC-06, qui prime.

## Décision

1. **Relevé horaire en base.** Chaque minute, la sonde de disponibilité verse aussi ses mesures dans
   `audit.releve_supervision`, une ligne par heure et par indicateur : la pire valeur de l'heure pour les
   centiles, les bracelets muets et la taille de la base, la somme des décomptes (messages reçus, messages
   écartés, requêtes) pour le reste. Chaque instance verse ce qu'elle a compté depuis son passage précédent, si
   bien que le relevé vaut pour l'ensemble des instances. Les relevés sont purgés à 90 jours, comme les sondes,
   et la purge est inscrite au registre.
2. **Évolution affichée.** Trente barres journalières pour la disponibilité (minutes notées sur minutes
   attendues depuis la première mesure), vingt-quatre barres horaires pour les autres indicateurs. Une période
   sans mesure est une barre vide, jamais une valeur inventée ; une mesure hors objectif est en violet.
3. **Latence des API.** Les métriques de Spring sont tenues route par route et leurs centiles ne se combinent
   pas : un chronomètre unique (`fasoguardian.api.duree`, filtre `MesureDesRequetes`) mesure toutes les routes
   `/api/`. Il publie le 95e centile pour l'écran et un histogramme pour la règle Prometheus, qui agrège les
   instances.
4. **Messages en erreur.** Le taux porte sur les messages des flux du broker (télémétrie, état, alerte,
   accusé) écartés parce que l'appareil est inconnu ou le message invalide. Les alertes reçues par SMS de
   repli passent par la même ingestion et y sont comptées : la tuile dit « Erreurs des bracelets », pas
   « Erreurs MQTT ». Une règle Prometheus alerte au-delà de 2 % sur 15 minutes.
5. **Espace disque.** Le serveur connaît la taille de la base (`pg_database_size`), pas la capacité du volume
   qui la porte. L'exploitation la déclare (`FG_CAPACITE_BASE`, par exemple `50GB`) ; l'écran rapporte alors
   l'occupation à l'objectif de 70 %. Sans déclaration, il montre la seule taille, sans pourcentage.
6. **Bracelets muets.** Leur nombre est une information ; la tuile ne passe en alerte que sur une hausse de plus
   de 10 % du parc en service en une heure, et dit la variation depuis l'heure précédente.

## Conséquences

- Nouvelle migration V34 ; l'API de supervision gagne les objectifs, la latence, le taux d'erreur, la taille de
  la base et les séries ; les champs existants restent.
- Les tuiles « Alertes ouvertes » et « Notifications » quittent l'écran, absentes de la maquette et de
  FG-DOC-06 ; ces décomptes restent dans l'API et dans Prometheus.
- L'alerte sur l'espace disque, qui demande une mesure du volume par l'hôte, reste à poser avec l'équipe
  d'exploitation en même temps que la destination des alertes (Alertmanager).
