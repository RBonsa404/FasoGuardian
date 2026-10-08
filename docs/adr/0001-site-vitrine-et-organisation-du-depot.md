# ADR 0001 — Site vitrine en quatrième application et organisation du dépôt

Statut : acceptée — 7 octobre 2026

## Contexte

FG-DOC-06 (§4.1, tableau 6) décrit trois applications Angular : `parents`, `public-qr` et `console`.
Le brief de développement et le paquet de design ajoutent un site vitrine (écrans 1 à 8), pré-rendu
pour le référencement, avec une scène 3D du bracelet chargée à la demande.
Le paquet de design range les projets sous `apps/` et `libs/` ; le brief impose `frontend/projects/`.

## Décision

- Le site vitrine est une quatrième application, `frontend/projects/site`, rendue côté serveur.
  Il ne consomme aucune API authentifiée et ne partage avec les autres applications que la bibliothèque `ui`.
- L'organisation du brief est retenue : `frontend/projects/{site,parents,public-qr,console,ui,api}`.
  Les noms `apps/` et `libs/` du paquet de design ne sont pas repris ; `libs/i18n` devient les fichiers de
  traduction de chaque application.

## Conséquences

Extension de FG-DOC-06 sans effet sur les trois autres applications. Three.js n'est une dépendance que
du site vitrine (et de l'écran 37 de `parents`, chargé à la demande) : il ne pèse pas sur le budget de
250 Ko de la PWA.
