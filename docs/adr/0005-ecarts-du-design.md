# ADR 0005 — Écarts et ajouts du paquet de design

Statut : acceptée en partie — 7 octobre 2026

## Contexte

Le paquet de design (HANDOFF.md, §10) signale sept écarts ou ajouts par rapport aux FG-DOC. Le design est
la source la moins prioritaire : chaque point est tranché ici.

## Décision

| N° | Écart du design | Décision |
|---|---|---|
| 1 | Couleur « attention » violette pour les avertissements non critiques | Acceptée : elle permet de réserver l'ambre aux alertes (FG-DOC-06 §5.3). Jetons `attention`, `attention-soft`, `attention-text` |
| 2 | Appels de la page QR par une ligne relais masquée | Acceptée sur le principe, **en attente du prestataire de téléphonie**. Un port `LigneRelais` est prévu avec un adaptateur bac à sable ; tant qu'aucun prestataire n'est retenu, le bouton d'appel compose le numéro du contact marqué visible sur le QR, comme le prévoient les FG-DOC |
| 3 | Notification du parent à chaque scan du QR | Acceptée : heure et quartier approximatif déduit de l'adresse IP pseudonymisée, sans identité du visiteur |
| 4 | Glisser pour acquitter, doublé d'un bouton | Acceptée |
| 5 | Code d'appairage distinct du QR | Déjà exigé par FG-DOC-05 (US-PAR-014) |
| 6 | Suivi 72 h après une déclaration de perte | Acceptée sous réserve de l'AIPD : le suivi prolongé d'un bracelet déclaré perdu est un traitement à documenter |
| 7 | Délai de réponse constant sur un QR invalide | Acceptée : complète US-SYS-010 |

## Conséquences

Les points 2 et 6 dépendent d'éléments externes (prestataire, AIPD) et sont signalés dans la matrice de
traçabilité.
