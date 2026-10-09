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

## Complément du 9 octobre 2026 — écrans d'administration de la console

- **Écran 69, « + Agent (code TOTP admin) ».** Le design demande un code TOTP à chaque création d'agent et dit
  que « toute modification exige le second facteur ». Tous les agents se connectent déjà avec un second
  facteur obligatoire, et la session d'un agent est courte (jeton de dix minutes, session de trente). La
  création, le changement de rôles et la suspension ne redemandent donc pas de code : ils sont réservés au
  rôle administrateur, journalisés, et un administrateur ne peut pas agir sur son propre compte. Redemander
  un code à chaque action se heurterait de plus à la protection contre le rejeu (un code ne sert qu'une fois
  par tranche de trente secondes).
- **Écran 69, colonne « CIL ».** La matrice du design a une colonne « CIL ». Le délégué à la protection des
  données n'a pas de compte (FG-DOC-05) : la colonne n'existe pas, ses rapports sont produits par
  l'administrateur.
- **Écran 69, matrice.** Elle présente les permissions réellement appliquées par le serveur, en lecture : le
  cloisonnement est dans le code et vérifié par les tests d'autorisation, il ne se règle pas à l'écran.
- **Écran 73, sources bloquées.** Le design montre des adresses IP partiellement masquées et un nombre
  d'essais. La plateforme ne conserve jamais l'adresse d'une source, seulement un pseudonyme (FG-DOC-06,
  tableau 18) : l'écran montre le début de ce pseudonyme et la date du blocage. Le seuil est celui de
  US-SYS-010 (plus de 20 jetons invalides en une minute), non les « 10 min » de la maquette.
- **Écrans 64 et 66, identité de l'enfant.** Voir ADR 0009 : le rôle SAV ne voit que l'état des bracelets.
