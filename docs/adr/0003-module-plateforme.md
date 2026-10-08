# ADR 0003 — Socle technique partagé `plateforme`

Statut : acceptée — 7 octobre 2026

## Contexte

FG-DOC-06 définit neuf modules métier. Plusieurs préoccupations techniques sont communes à tous : format
d'erreur RFC 9457, configuration de sécurité HTTP, contrat OpenAPI, verrouillage des traitements planifiés.
Les placer dans un module métier créerait des dépendances artificielles ; les dupliquer serait pire.

## Décision

Un dixième paquet, `bf.fasoguardian.plateforme`, déclaré module Spring Modulith ouvert et partagé, porte ces
préoccupations. Il ne contient aucune règle métier et ne dépend d'aucun module métier. Son schéma PostgreSQL
`plateforme` héberge l'historique Flyway, les verrous ShedLock et le registre de publication des événements.

## Conséquences

Le test `ModulariteTest` vérifie la liste exacte des modules : neuf modules métier et ce socle.
Toute classe ajoutée à `plateforme` doit rester sans connaissance du domaine.
