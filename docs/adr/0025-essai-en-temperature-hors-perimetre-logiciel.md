# ADR 0025 — Tenue au-delà de 50 °C : un essai matériel, hors du périmètre logiciel

Statut : acceptée — 9 octobre 2026

## Contexte

US-SYS-009 (COULD, REQ-COULD-04) : « étant donné un essai en étuve sur la plage élargie, quand il est réalisé,
alors localisation, alerte et communication restent opérantes ». Précondition : un prototype de boîtier
renforcé.

## Décision

Cette user story ne se réalise pas par du code : son critère est un essai en étuve sur un prototype qui
n'existe pas encore. Elle reste **« À faire »** dans la traçabilité, avec ce motif, et ne sera pas déclarée
terminée sur la foi d'un essai logiciel.

Ce que le logiciel apporte déjà à cet essai, sans le remplacer :

- la plateforme reçoit et affiche en continu positions, alertes et état du bracelet : l'opérateur de l'essai
  y lit si les trois fonctions restent opérantes pendant la montée en température ;
- la supervision signale un bracelet devenu muet (US-SAV-002) ;
- le micrologiciel surveille la température de la batterie et applique le profil de charge de FG-DOC-08 §5.4.

## Point à trancher

- Fabriquer le prototype de boîtier renforcé et commander l'essai en étuve : décision matérielle et budgétaire.
